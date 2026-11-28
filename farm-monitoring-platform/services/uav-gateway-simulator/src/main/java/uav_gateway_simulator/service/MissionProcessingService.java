
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionProcessingService {

    private final MissionExecutionService missionExecutionService;
    private final MissionExecutionTracker executionTracker;
    private final MissionResultSimulationService missionResultSimulationService;
    private final MissionResultPayloadStore payloadStore;
    private final ExecutorService simulationExecutor;
    private final MissionResultsRetryScheduler retryScheduler;
    private final MissionClient missionClient;
    private final MissionRecoveryLeaseManager recoveryLeaseManager;
    private final SimulatorInstanceIdentity simulatorInstanceIdentity;
    private final RecoveryLeaseRegistry leaseRegistry;
    private final FlightLifecycleService flightLifecycleService;

    public void submit(MissionReadyPayload payload) {
        if (payload == null || payload.getExecutionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid mission.ready payload"
            );
        }

        UUID executionId = payload.getExecutionId();
        UUID missionId = payload.getMissionId();
        FlightOwnershipToken ownershipToken =
                new FlightOwnershipToken(
                        executionId,
                        null,
                        null
                );

        // Retry Results without re-executing ACK or Waypoints
        if (executionTracker.getStatus(executionId)
                == MissionExecutionTracker.Status.RESULTS_RETRY) {

            log.info(
                    "Mission Results retry already pending: executionId={}",
                    executionId
            );

            return;
        }

        // Ignore duplicate processing or completed executions
        if (!executionTracker.tryStart(executionId)) {
            log.info(
                    "Duplicate execution ignored: executionId={}",
                    executionId
            );
            return;
        }

        try {
            simulationExecutor.execute(() -> {
                try {
                    missionExecutionService.prepareMission(payload);


                    boolean completed = leaseRegistry.runIfCurrent(
                            ownershipToken,
                            () -> {

                                if (!executionTracker.complete(executionId)) {
                                    throw new IllegalStateException(
                                            "Cannot complete mission execution: "
                                                    + executionId
                                    );
                                }

                                removeOwnedPayload(ownershipToken);
                            }
                    );

                    if (!completed) {
                        log.warn(
                                "Ignoring stale mission completion: executionId={}",
                                executionId
                        );

                        return;
                    }


                    log.info(
                            "Mission simulation completed: executionId={}",
                            executionId
                    );

                }
                catch (MissionCancelledException e) {

                    boolean cancelled = leaseRegistry.runIfCurrent(
                            ownershipToken,
                            () -> {

                                executionTracker.cancel(executionId);

                                removeOwnedPayload(ownershipToken);

                                flightLifecycleService.onCancelled(
                                        missionId,
                                        executionId,
                                        ownershipToken
                                );
                            }
                    );

                    if (!cancelled) {

                        log.warn(
                                "Ignoring stale mission cancellation: executionId={}",
                                executionId
                        );

                        return;
                    }

                    log.info(
                            "Mission simulation cancelled: missionId={}, executionId={}",
                            missionId,
                            executionId
                    );
                }
                catch (Exception e) {
                    // Mark processing failure as FAILED.
                    // Preserve RESULTS_RETRY for the retry scheduler.
                    boolean handled = leaseRegistry.runIfCurrent(
                            ownershipToken,
                            () -> executionTracker.fail(executionId)
                    );

                    if (!handled) {

                        log.warn(
                                "Ignoring stale mission failure: executionId={}",
                                executionId
                        );

                        return;
                    }

                    if (executionTracker.getStatus(executionId)
                            == MissionExecutionTracker.Status.RESULTS_RETRY) {

                        var pending = payloadStore.get(executionId);

                        if (pending != null
                                && ownershipToken.equals(
                                pending.ownershipToken()
                        )) {

                            retryScheduler.schedule(
                                    executionId,
                                    ownershipToken
                            );
                        } else {
                            log.error(
                                    "Cannot retry Mission Results because payload is missing: executionId={}",
                                    executionId
                            );
                        }
                    }

                    log.error(
                            "Mission simulation failed: executionId={}",
                            executionId,
                            e
                    );
                }
            });


        } catch (RuntimeException e) {
            leaseRegistry.runIfCurrent(
                    ownershipToken,
                    () -> executionTracker.releaseBeforeStart(
                            executionId
                    )
            );

            log.error(
                    "Failed to submit mission execution: executionId={}",
                    executionId,
                    e
            );

            throw e;
        }

    }


    public boolean recover(MissionReadyPayload payload) {

        if (payload == null
                || payload.getExecutionId() == null
                || payload.getMissionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid mission recovery payload"
            );
        }

        UUID missionId = payload.getMissionId();
        UUID executionId = payload.getExecutionId();

        MissionExecutionTracker.Status currentStatus =
                executionTracker.getStatus(executionId);

        if (currentStatus == MissionExecutionTracker.Status.RESULTS_PENDING
                || currentStatus == MissionExecutionTracker.Status.RESULTS_RETRY
                || currentStatus == MissionExecutionTracker.Status.COMPLETED
                || currentStatus == MissionExecutionTracker.Status.CANCELLED) {

            log.info(
                    "Recovery skipped: executionId={}, status={}",
                    executionId,
                    currentStatus
            );

            return false;
        }

        if (!executionTracker.tryStartRecovery(executionId)) {
            log.info(
                    "Recovery already active: executionId={}",
                    executionId
            );
            return false;
        }
        // Mark this execution as recovery before submitting the worker.
        leaseRegistry.beginRecovery(executionId);

        try {
            simulationExecutor.execute(() -> {

                boolean claimed = false;
                FlightOwnershipToken ownershipToken = null;

                try {
                    // 1. Claim ownership trước khi resume.
                    MissionRecoveryClaimResponse claim =
                            missionClient.claimRecovery(
                                    missionId,
                                    executionId,
                                    simulatorInstanceIdentity.getInstanceId()
                            );

                    if (claim == null
                            || !missionId.equals(claim.missionId())
                            || !executionId.equals(claim.executionId())
                            || !simulatorInstanceIdentity.getInstanceId()
                            .equals(claim.instanceId())
                            || claim.leaseUntil() == null
                            || !claim.leaseUntil().isAfter(
                            java.time.Instant.now().plusSeconds(5))) {

                        throw new IllegalStateException(
                                "Invalid recovery claim: " + executionId
                        );
                    }

                    // 2. Register lease để Heartbeat,
                    // Waypoint và Results có ownership.
                    recoveryLeaseManager.register(claim);

                    ownershipToken = new FlightOwnershipToken(
                            executionId,
                            claim.instanceId(),
                            claim.recoveryGeneration()
                    );

                    claimed = true;
                    flightLifecycleService.onRecovering(
                            missionId,
                            executionId,
                            ownershipToken
                    );

                    log.info(
                            "Recovery claimed: missionId={}, executionId={}, generation={}, leaseUntil={}",
                            missionId,
                            executionId,
                            claim.recoveryGeneration(),
                            claim.leaseUntil()
                    );

                    // 3. Resume từ waypoint chưa hoàn thành.
                    missionExecutionService.resumeMission(payload);

                    FlightOwnershipToken completedToken =
                            ownershipToken;

                    boolean completed = leaseRegistry.runIfCurrent(
                            completedToken,
                            () -> {

                                if (!executionTracker.complete(executionId)) {
                                    throw new IllegalStateException(
                                            "Cannot complete recovered execution: "
                                                    + executionId
                                    );
                                }

                                removeOwnedPayload(completedToken);

                                executionTracker.clearRecoveryTracking(
                                        executionId
                                );

                                recoveryLeaseManager.removeIfCurrent(
                                        completedToken
                                );
                            }
                    );

                    if (!completed) {
                        log.warn(
                                "Ignoring stale recovery completion: executionId={}",
                                executionId
                        );

                        return;
                    }

                    log.info(
                            "Mission recovery completed: executionId={}",
                            executionId
                    );


                }
                catch (MissionCancelledException e) {

                    if (claimed) {

                        FlightOwnershipToken cancelledToken =
                                ownershipToken;

                        boolean cancelled = leaseRegistry.runIfCurrent(
                                cancelledToken,
                                () -> {

                                    executionTracker.cancel(executionId);

                                    removeOwnedPayload(cancelledToken);

                                    try {

                                        flightLifecycleService.onCancelled(
                                                missionId,
                                                executionId,
                                                cancelledToken
                                        );

                                    } catch (IllegalStateException ex) {

                                        log.warn(
                                                "Recovery cancellation telemetry update rejected: executionId={}",
                                                executionId,
                                                ex
                                        );

                                    } finally {

                                        executionTracker.clearRecoveryTracking(
                                                executionId
                                        );

                                        recoveryLeaseManager.removeIfCurrent(
                                                cancelledToken
                                        );
                                    }
                                }
                        );

                        if (!cancelled) {
                            log.warn(
                                    "Ignoring stale recovery cancellation: executionId={}",
                                    executionId
                            );

                            return;
                        }

                    } else {

                        leaseRegistry.runBeforeClaim(
                                executionId,
                                () -> {

                                    executionTracker.cancel(executionId);

                                    flightLifecycleService.onCancelledBeforeClaim(
                                            missionId,
                                            executionId
                                    );

                                    executionTracker.clearRecoveryTracking(
                                            executionId
                                    );

                                    leaseRegistry.cancelBeforeClaim(
                                            executionId
                                    );
                                }
                        );
                    }

                    log.info(
                            "Mission recovery cancelled: missionId={}, executionId={}",
                            missionId,
                            executionId
                    );
                }
                catch (RejectedExecutionException e) {

                    if (claimed) {

                        FlightOwnershipToken rejectedToken =
                                ownershipToken;

                        leaseRegistry.runIfCurrent(
                                rejectedToken,
                                () -> {

                                    executionTracker.fail(executionId);

                                    executionTracker.clearRecoveryTracking(
                                            executionId
                                    );

                                    recoveryLeaseManager.markLostIfCurrent(
                                            rejectedToken
                                    );
                                }
                        );

                    } else {

                        leaseRegistry.runBeforeClaim(
                                executionId,
                                () -> {

                                    executionTracker.rollbackRecovery(
                                            executionId
                                    );

                                    leaseRegistry.cancelBeforeClaim(
                                            executionId
                                    );
                                }
                        );
                    }

                    log.error(
                            "Recovery task rejected: executionId={}",
                            executionId,
                            e
                    );

                    throw e;
                }

                catch (Exception e) {

                    if (!claimed) {

                        leaseRegistry.runBeforeClaim(
                                executionId,
                                () -> {

                                    executionTracker.fail(executionId);

                                    executionTracker.clearRecoveryTracking(
                                            executionId
                                    );

                                    leaseRegistry.cancelBeforeClaim(
                                            executionId
                                    );
                                }
                        );

                        log.error(
                                "Recovery failed before claim: executionId={}",
                                executionId,
                                e
                        );

                        return;
                    }

                    FlightOwnershipToken failedToken =
                            ownershipToken;

                    boolean handled = leaseRegistry.runIfCurrent(
                            failedToken,
                            () -> {

                                executionTracker.fail(executionId);

                                executionTracker.clearRecoveryTracking(
                                        executionId
                                );

                                if (!recoveryLeaseManager.hasValidLease(
                                        executionId
                                )) {

                                    recoveryLeaseManager.markLostIfCurrent(
                                            failedToken
                                    );
                                }
                            }
                    );

                    if (!handled) {

                        boolean lostOwnerHandled =
                                leaseRegistry.runIfLostOwner(
                                        failedToken,
                                        () -> {

                                            executionTracker.failDueToOwnershipLoss(
                                                    executionId
                                            );

                                            executionTracker.clearRecoveryTracking(
                                                    executionId
                                            );
                                        }
                                );

                        if (lostOwnerHandled) {

                            log.warn(
                                    "Recovery failed due to ownership loss: executionId={}",
                                    executionId,
                                    e
                            );

                        } else {

                            log.warn(
                                    "Ignoring stale recovery failure: executionId={}",
                                    executionId
                            );
                        }

                        return;
                    }

                    if (executionTracker.getStatus(executionId)
                            == MissionExecutionTracker.Status.RESULTS_RETRY) {

                        var pending = payloadStore.get(executionId);

                        if (pending != null
                                && failedToken.equals(
                                pending.ownershipToken()
                        )
                                && recoveryLeaseManager.hasValidLease(
                                executionId
                        )) {

                            retryScheduler.schedule(
                                    executionId,
                                    failedToken
                            );
                        } else {

                            log.warn(
                                    "Recovery Results retry skipped: executionId={}",
                                    executionId
                            );
                        }
                    }

                    log.error(
                            "Mission recovery failed: executionId={}",
                            executionId,
                            e
                    );
                }
            });

            return true;

        } catch (java.util.concurrent.RejectedExecutionException e) {

            leaseRegistry.runBeforeClaim(
                    executionId,
                    () -> {

                        executionTracker.rollbackRecovery(
                                executionId
                        );

                        leaseRegistry.cancelBeforeClaim(
                                executionId
                        );
                    }
            );

            log.error(
                    "Recovery task rejected: executionId={}",
                    executionId,
                    e
            );

            throw e;
        }
    }

    private void removeOwnedPayload(
            FlightOwnershipToken ownershipToken
    ) {
        var pending = payloadStore.get(
                ownershipToken.executionId()
        );

        if (pending != null
                && ownershipToken.equals(
                pending.ownershipToken()
        )) {

            payloadStore.remove(pending);
        }
    }
}
