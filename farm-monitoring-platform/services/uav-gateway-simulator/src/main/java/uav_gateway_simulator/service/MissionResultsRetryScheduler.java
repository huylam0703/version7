
package uav_gateway_simulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MissionResultsRetryScheduler {

    private final MissionExecutionTracker executionTracker;
    private final MissionResultSimulationService resultSimulationService;
    private final MissionResultPayloadStore payloadStore;
    private final MissionRecoveryLeaseManager recoveryLeaseManager;
    private final ScheduledExecutorService scheduler;
    private final MissionCancellationService cancellationService;
    private final FlightLifecycleService flightLifecycleService;
    private final RecoveryLeaseRegistry leaseRegistry;

    private final ConcurrentMap<UUID, RetryState> retryStates =
            new ConcurrentHashMap<>();

    @Value("${simulator.results.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${simulator.results.retry.delay-ms:5000}")
    private long retryDelayMs;

    @Value("${simulator.results.retry.max-attempts:3}")
    private int maxAttempts;

    public MissionResultsRetryScheduler(
            MissionExecutionTracker executionTracker,
            MissionResultSimulationService resultSimulationService,
            MissionResultPayloadStore payloadStore,
            MissionRecoveryLeaseManager recoveryLeaseManager,
            MissionCancellationService cancellationService,
            FlightLifecycleService flightLifecycleService,
            RecoveryLeaseRegistry leaseRegistry,
            @Qualifier("missionResultsRetryExecutor")
            ScheduledExecutorService scheduler
    ) {
        this.executionTracker = executionTracker;
        this.resultSimulationService = resultSimulationService;
        this.payloadStore = payloadStore;
        this.recoveryLeaseManager = recoveryLeaseManager;
        this.cancellationService = cancellationService;
        this.flightLifecycleService = flightLifecycleService;
        this.leaseRegistry = leaseRegistry;
        this.scheduler = scheduler;
    }

    private static class RetryState {

        private final FlightOwnershipToken ownershipToken;

        private final AtomicInteger attempts =
                new AtomicInteger(0);

        private boolean scheduled;
        private boolean running;
        private boolean exhausted;

        private RetryState(
                FlightOwnershipToken ownershipToken
        ) {
            this.ownershipToken = ownershipToken;
        }
    }

    public void schedule(UUID executionId) {

        if (!retryEnabled || executionId == null) {
            return;
        }

        MissionResultPayloadStore.PendingResult pending =
                payloadStore.get(executionId);

        if (pending == null) {
            return;
        }

        schedule(
                executionId,
                pending.ownershipToken()
        );
    }

    public void schedule(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {

        if (!retryEnabled
                || executionId == null
                || ownershipToken == null
                || !executionId.equals(
                ownershipToken.executionId()
        )) {
            return;
        }

        leaseRegistry.runIfCurrent(
                ownershipToken,
                () -> {

                    if (!isOwnershipValid(ownershipToken)) {
                        return;
                    }

                    if (executionTracker.getStatus(executionId)
                            != MissionExecutionTracker.Status.RESULTS_RETRY) {
                        return;
                    }

                    MissionResultPayloadStore.PendingResult pending =
                            payloadStore.get(executionId);

                    if (pending == null
                            || !ownershipToken.equals(
                            pending.ownershipToken()
                    )) {
                        return;
                    }

                    RetryState state = retryStates.compute(
                            executionId,
                            (id, existing) -> {

                                if (existing != null
                                        && existing.ownershipToken.equals(
                                        ownershipToken
                                )) {
                                    return existing;
                                }

                                return new RetryState(
                                        ownershipToken
                                );
                            }
                    );

                    synchronized (state) {

                        if (state.scheduled
                                || state.running
                                || state.exhausted) {
                            return;
                        }

                        if (state.attempts.get() >= maxAttempts) {
                            state.exhausted = true;
                            return;
                        }

                        state.scheduled = true;

                        try {

                            scheduler.schedule(
                                    () -> executeRetry(
                                            executionId,
                                            state
                                    ),
                                    retryDelayMs,
                                    TimeUnit.MILLISECONDS
                            );

                            log.info(
                                    "Mission Results retry scheduled: executionId={}, delayMs={}, token={}",
                                    executionId,
                                    retryDelayMs,
                                    ownershipToken
                            );

                        } catch (RuntimeException e) {

                            state.scheduled = false;

                            throw e;
                        }
                    }
                }
        );
    }

    private void executeRetry(
            UUID executionId,
            RetryState state
    ) {
        FlightOwnershipToken ownershipToken =
                state.ownershipToken;

        AtomicBoolean started = new AtomicBoolean(false);

        boolean currentOwner = leaseRegistry.runIfCurrent(
                ownershipToken,
                () -> {

                    if (retryStates.get(executionId) != state
                            || !isOwnershipValid(ownershipToken)) {
                        return;
                    }

                    synchronized (state) {

                        state.scheduled = false;

                        if (state.running
                                || state.exhausted
                                || state.attempts.get() >= maxAttempts) {
                            return;
                        }

                        MissionResultPayloadStore.PendingResult pending =
                                payloadStore.get(executionId);

                        if (pending == null
                                || !ownershipToken.equals(
                                pending.ownershipToken()
                        )) {
                            return;
                        }

                        if (!executionTracker.tryStartResultsRetry(
                                executionId
                        )) {
                            return;
                        }

                        state.running = true;

                        started.set(true);
                    }
                }
        );

        if (!currentOwner) {

            retryStates.remove(executionId, state);

            return;
        }

        if (!started.get()) {

            if (!isOwnershipValid(ownershipToken)) {
                retryStates.remove(executionId, state);
            }

            return;
        }

        boolean cancelled = false;

        int attempt = state.attempts.incrementAndGet();
        boolean succeeded = false;
        boolean ownershipLost = false;

        try {

            // Recheck ownership immediately before sending.
            if (!isOwnershipValid(ownershipToken)) {
                throw new RecoveryOwnershipLostException(
                        "Recovery ownership is invalid: " + executionId
                );
            }

            MissionResultPayloadStore.PendingResult pending =
                    payloadStore.get(executionId);

            if (pending == null) {
                throw new IllegalStateException(
                        "Mission Results payload missing: " + executionId
                );
            }
            if (!pending.ownershipToken().equals(ownershipToken)) {
                throw new RecoveryOwnershipLostException(
                        "Pending Results ownership changed: "
                                + executionId
                );
            }

            cancellationService.checkNotCancelled(pending.missionId());

            if (!isOwnershipValid(ownershipToken)) {
                throw new RecoveryOwnershipLostException(
                        "Results retry ownership lost: " + executionId
                );
            }

            resultSimulationService.retryResults(
                    executionId,
                    ownershipToken
            );

            if (!isOwnershipValid(ownershipToken)) {
                throw new RecoveryOwnershipLostException(
                        "Results retry ownership changed: " + executionId
                );
            }

            boolean completed = leaseRegistry.runIfCurrent(
                    ownershipToken,
                    () -> {

                        if (!isOwnershipValid(ownershipToken)) {
                            throw new RecoveryOwnershipLostException(
                                    "Results retry ownership expired: "
                                            + executionId
                            );
                        }

                        if (!executionTracker.complete(executionId)) {
                            throw new IllegalStateException(
                                    "Cannot complete Mission Results retry: "
                                            + executionId
                            );
                        }

                        payloadStore.remove(pending);

                        recoveryLeaseManager.removeIfCurrent(
                                ownershipToken
                        );
                    }
            );

            if (!completed) {
                throw new RecoveryOwnershipLostException(
                        "Results retry completion ownership lost: "
                                + executionId
                );
            }

            succeeded = true;

            log.info(
                    "Mission Results retry succeeded: executionId={}, attempt={}",
                    executionId,
                    attempt
            );

        }
        catch (MissionCancelledException e) {

            boolean handled = leaseRegistry.runIfCurrent(
                    ownershipToken,
                    () -> {

                        MissionResultPayloadStore.PendingResult pending =
                                payloadStore.get(executionId);

                        if (pending == null
                                || !ownershipToken.equals(
                                pending.ownershipToken()
                        )) {
                            return;
                        }

                        executionTracker.cancel(executionId);

                        try {

                            if (isOwnershipValid(ownershipToken)) {
                                flightLifecycleService.onCancelled(
                                        pending.missionId(),
                                        executionId,
                                        ownershipToken
                                );
                            }

                        } catch (IllegalStateException ex) {

                            log.warn(
                                    "Cancellation telemetry skipped: executionId={}",
                                    executionId,
                                    ex
                            );

                        } finally {

                            payloadStore.remove(pending);

                            recoveryLeaseManager.removeIfCurrent(
                                    ownershipToken
                            );
                        }
                    }
            );

            if (handled) {

                cancelled = true;

                log.info(
                        "Mission Results retry cancelled: executionId={}",
                        executionId
                );

            } else {

                ownershipLost = true;

                log.warn(
                        "Ignoring stale retry cancellation: executionId={}",
                        executionId
                );
            }
        }
        catch (RecoveryOwnershipLostException e) {

            ownershipLost = true;

            log.error(
                    "Mission Results retry stopped: ownership lost, executionId={}",
                    executionId,
                    e
            );

        } catch (IllegalStateException e) {

            if (!isOwnershipValid(ownershipToken)) {

                ownershipLost = true;

                log.error(
                        "Mission Results retry stopped: invalid lease, executionId={}",
                        executionId,
                        e
                );

            } else {
                if (!markRetryPending(executionId, ownershipToken)) {
                    ownershipLost = true;
                }

                log.error(
                        "Mission Results retry failed: executionId={}, attempt={}",
                        executionId,
                        attempt,
                        e
                );
            }

        } catch (Exception e) {

            if (!isOwnershipValid(ownershipToken)) {
                ownershipLost = true;
            } else {
                if (!markRetryPending(executionId, ownershipToken)) {
                    ownershipLost = true;
                }
            }

            log.error(
                    "Mission Results retry failed: executionId={}, attempt={}",
                    executionId,
                    attempt,
                    e
            );
        } finally {

            synchronized (state) {
                state.running = false;

                if (cancelled
                        || ownershipLost
                        || (!succeeded && attempt >= maxAttempts)) {
                    state.exhausted = true;
                }
            }
        }
        if (cancelled) {
            retryStates.remove(executionId, state);
            return;
        }

        if (ownershipLost) {
            retryStates.remove(executionId, state);

            log.warn(
                    "Stale Results retry stopped: executionId={}, token={}",
                    executionId,
                    ownershipToken
            );

            return;
        }

        if (succeeded) {
            retryStates.remove(executionId, state);
            return;
        }

        if (attempt < maxAttempts) {

            leaseRegistry.runIfCurrent(
                    ownershipToken,
                    () -> {

                        if (retryStates.get(executionId) == state
                                && isOwnershipValid(ownershipToken)) {

                            schedule(
                                    executionId,
                                    ownershipToken
                            );
                        }
                    }
            );

        } else {
            log.error(
                    "Mission Results retry exhausted: executionId={}, maxAttempts={}",
                    executionId,
                    maxAttempts
            );
        }
    }

    private boolean isOwnershipValid(
            FlightOwnershipToken ownershipToken
    ) {
        if (ownershipToken == null) {
            return false;
        }

        try {
            var lease = recoveryLeaseManager.getValidLeaseOrNull(
                    ownershipToken.executionId()
            );

            FlightOwnershipToken currentToken;

            if (lease == null) {
                currentToken = new FlightOwnershipToken(
                        ownershipToken.executionId(),
                        null,
                        null
                );
            } else {
                currentToken = new FlightOwnershipToken(
                        ownershipToken.executionId(),
                        lease.instanceId(),
                        lease.recoveryGeneration()
                );
            }

            return ownershipToken.equals(currentToken);

        } catch (IllegalStateException e) {
            return false;
        }
    }

    private boolean markRetryPending(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {

        AtomicBoolean handled = new AtomicBoolean(false);

        boolean currentOwner = leaseRegistry.runIfCurrent(
                ownershipToken,
                () -> {

                    if (!isOwnershipValid(ownershipToken)) {
                        return;
                    }

                    if (executionTracker.getStatus(executionId)
                            == MissionExecutionTracker.Status.RESULTS_PENDING) {

                        executionTracker.markResultsRetry(
                                executionId
                        );
                    }

                    handled.set(true);
                }
        );

        return currentOwner && handled.get();
    }


}
