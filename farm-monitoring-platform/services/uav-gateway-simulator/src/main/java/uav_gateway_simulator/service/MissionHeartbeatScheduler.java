
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionHeartbeatScheduler {

    private final MissionClient missionClient;
    private final ScheduledExecutorService heartbeatExecutor;
    private final RecoveryLeaseRegistry leaseRegistry;
    private final MissionCancellationService cancellationService;
    private final FlightLifecycleService flightLifecycleService;
    private final FlightOwnershipService flightOwnershipService;

    private final ConcurrentMap<UUID, HeartbeatSession>
            activeHeartbeats = new ConcurrentHashMap<>();

    @Value("${simulator.heartbeat.interval-ms:10000}")
    private long heartbeatIntervalMs;

    @Value("${simulator.heartbeat.failure-threshold:3}")
    private int heartbeatFailureThreshold = 3;

    public void start(
            UUID missionId,
            UUID executionId
    ) {
        start(
                missionId,
                executionId,
                flightOwnershipService.capture(executionId)
        );
    }

    public void start(
            UUID missionId,
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        if (missionId == null || executionId == null) {
            throw new IllegalArgumentException(
                    "MissionId and executionId are required"
            );
        }

        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "Heartbeat interval must be positive"
            );
        }

        if (heartbeatFailureThreshold <= 0) {
            throw new IllegalArgumentException(
                    "Heartbeat failure threshold must be positive"
            );
        }

        if (ownershipToken == null
                || !executionId.equals(ownershipToken.executionId())) {

            throw new IllegalArgumentException(
                    "Heartbeat ownership token mismatch"
            );
        }

        flightOwnershipService.validate(ownershipToken);

        HeartbeatSession candidate =
                new HeartbeatSession(ownershipToken);

        HeartbeatSession selected;

        synchronized (leaseRegistry) {

            flightOwnershipService.validate(ownershipToken);

            selected = activeHeartbeats.compute(
                    executionId,
                    (id, existing) -> {

                        if (existing != null
                                && !existing.isCancelled()
                                && existing.ownershipToken()
                                .equals(ownershipToken)) {

                            return existing;
                        }

                        if (existing != null) {

                            existing.cancel();

                            log.info(
                                    "Replacing previous heartbeat session: executionId={}",
                                    executionId
                            );
                        }

                        return candidate;
                    }
            );
        }

        if (selected != candidate) {
            return;
        }

        try {
            ScheduledFuture<?> future =
                    heartbeatExecutor.scheduleWithFixedDelay(
                            () -> executeHeartbeat(
                                    missionId,
                                    executionId,
                                    candidate
                            ),
                            0,
                            heartbeatIntervalMs,
                            TimeUnit.MILLISECONDS
                    );

            candidate.attachFuture(future);

            log.info(
                    "Heartbeat session started: missionId={}, executionId={}, generation={}",
                    missionId,
                    executionId,
                    ownershipToken.recoveryGeneration()
            );

        } catch (RuntimeException e) {

            stopSession(executionId, candidate);

            throw e;
        }
    }

    private void executeHeartbeat(
            UUID missionId,
            UUID executionId,
            HeartbeatSession session
    ) {
        if (!ensureActive(executionId, session)) {
            return;
        }

        try {
            missionClient.sendHeartbeat(
                    missionId,
                    executionId
            );

            if (!ensureActive(executionId, session)) {
                return;
            }

            int previousFailures =
                    session.consecutiveFailures()
                            .getAndSet(0);

            updateTelemetryIfActive(
                    executionId,
                    session,
                    () -> flightLifecycleService.onReconnected(
                            missionId,
                            executionId,
                            session.ownershipToken()
                    )
            );

            if (previousFailures > 0) {

                log.info(
                        "Heartbeat connection restored: executionId={}, previousFailures={}",
                        executionId,
                        previousFailures
                );
            }

            log.debug(
                    "Heartbeat confirmed: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

        } catch (RestClientResponseException e) {

            handleHttpError(
                    missionId,
                    executionId,
                    session,
                    e
            );

        } catch (IllegalStateException e) {

            log.error(
                    "Heartbeat stopped due to invalid local state: executionId={}",
                    executionId,
                    e
            );

            stopSession(executionId, session);

        } catch (Exception e) {

            recordFailure(
                    missionId,
                    executionId,
                    session,
                    e
            );
        }
    }

    private void handleHttpError(
            UUID missionId,
            UUID executionId,
            HeartbeatSession session,
            RestClientResponseException exception
    ) {
        if (!ensureActive(executionId, session)) {
            return;
        }

        int status = exception.getStatusCode().value();

        if (status == 403 || status == 409) {

            log.error(
                    "Heartbeat rejected: executionId={}, status={}",
                    executionId,
                    status
            );

            synchronized (leaseRegistry) {

                activeHeartbeats.computeIfPresent(
                        executionId,
                        (id, current) -> {

                            if (current != session) {
                                return current;
                            }

                            boolean markedLost =
                                    leaseRegistry.markLostIfCurrent(
                                            session.ownershipToken()
                                    );

                            if (!markedLost) {

                                log.warn(
                                        "Ignoring stale heartbeat ownership loss: executionId={}, generation={}",
                                        executionId,
                                        session.ownershipToken()
                                                .recoveryGeneration()
                                );
                            }

                            session.cancel();

                            return null;
                        }
                );
            }

            return;
        }

        if (status == 400) {

            try {
                boolean cancelled =
                        cancellationService.isCancelled(
                                missionId
                        );

                if (!ensureActive(executionId, session)) {
                    return;
                }

                if (cancelled) {

                    updateTelemetryIfActive(
                            executionId,
                            session,
                            () -> flightLifecycleService.onCancelled(
                                    missionId,
                                    executionId,
                                    session.ownershipToken()
                            )
                    );

                    log.info(
                            "Heartbeat stopped because mission was cancelled: executionId={}",
                            executionId
                    );

                    stopSession(executionId, session);

                } else {

                    log.error(
                            "Heartbeat HTTP 400 but mission is not cancelled: executionId={}",
                            executionId,
                            exception
                    );
                }

            } catch (IllegalStateException e) {

                if (!ensureActive(executionId, session)) {
                    return;
                }

                log.error(
                        "Cannot update heartbeat cancellation: executionId={}",
                        executionId,
                        e
                );

            } catch (Exception e) {

                log.error(
                        "Cannot verify mission cancellation: executionId={}",
                        executionId,
                        e
                );
            }

            return;
        }

        if (status >= 500 || status == 429) {

            recordFailure(
                    missionId,
                    executionId,
                    session,
                    exception
            );

            return;
        }

        log.error(
                "Heartbeat HTTP error: executionId={}, status={}",
                executionId,
                status,
                exception
        );
    }

    private boolean hasValidOwnership(
            HeartbeatSession session
    ) {
        try {
            flightOwnershipService.validate(
                    session.ownershipToken()
            );

            return true;

        } catch (IllegalStateException e) {

            log.warn(
                    "Heartbeat ownership lost: executionId={}, reason={}",
                    session.ownershipToken().executionId(),
                    e.getMessage()
            );

            return false;
        }
    }

    private boolean ensureActive(
            UUID executionId,
            HeartbeatSession session
    ) {
        if (session.isCancelled()
                || activeHeartbeats.get(executionId) != session) {

            stopSession(executionId, session);

            return false;
        }

        if (!hasValidOwnership(session)) {

            stopSession(executionId, session);

            return false;
        }

        return true;
    }

    private void recordFailure(
            UUID missionId,
            UUID executionId,
            HeartbeatSession session,
            Exception exception
    ) {

        if (!ensureActive(executionId, session)) {
            return;
        }

        int failures =
                session.consecutiveFailures()
                        .updateAndGet(
                                current ->
                                        current < Integer.MAX_VALUE
                                                ? current + 1
                                                : current
                        );

        log.warn(
                "Heartbeat failure: executionId={}, failures={}, reason={}",
                executionId,
                failures,
                exception.getMessage()
        );

        if (failures == heartbeatFailureThreshold
                && ensureActive(executionId, session)) {

            try {

                updateTelemetryIfActive(
                        executionId,
                        session,
                        () -> flightLifecycleService.onDisconnected(
                                missionId,
                                executionId,
                                session.ownershipToken()
                        )
                );

                log.warn(
                        "Heartbeat disconnected: executionId={}",
                        executionId
                );

            } catch (IllegalStateException e) {

                log.warn(
                        "Heartbeat disconnected update rejected: executionId={}, generation={}",
                        executionId,
                        session.ownershipToken()
                                .recoveryGeneration(),
                        e
                );

                stopSession(executionId, session);
            }
        }
    }

    private void updateTelemetryIfActive(
            UUID executionId,
            HeartbeatSession session,
            Runnable action
    ) {
        flightOwnershipService.executeIfCurrent(
                session.ownershipToken(),
                () -> activeHeartbeats.computeIfPresent(
                        executionId,
                        (id, current) -> {

                            if (current != session
                                    || session.isCancelled()) {
                                return current;
                            }

                            action.run();

                            return current;
                        }
                )
        );
    }

    private void stopSession(
            UUID executionId,
            HeartbeatSession session
    ) {
        // Remove only this exact session.
        activeHeartbeats.remove(
                executionId,
                session
        );

        session.cancel();
    }

    public void stop(UUID executionId) {

        HeartbeatSession session =
                activeHeartbeats.remove(executionId);

        if (session != null) {

            session.cancel();

            log.info(
                    "Heartbeat session stopped: executionId={}",
                    executionId
            );
        }
    }


    public void stop(FlightOwnershipToken ownershipToken) {

        if (ownershipToken == null) {
            return;
        }

        UUID executionId = ownershipToken.executionId();

        HeartbeatSession session =
                activeHeartbeats.get(executionId);

        if (session == null) {
            return;
        }

        if (!session.ownershipToken().equals(ownershipToken)) {

            log.warn(
                    "Ignoring stale heartbeat stop: executionId={}, generation={}",
                    executionId,
                    ownershipToken.recoveryGeneration()
            );

            return;
        }

        stopSession(executionId, session);

        log.info(
                "Heartbeat session stopped: executionId={}, generation={}",
                executionId,
                ownershipToken.recoveryGeneration()
        );
    }

}
