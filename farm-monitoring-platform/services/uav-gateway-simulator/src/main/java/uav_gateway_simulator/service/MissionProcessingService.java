
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.MissionRecoveryClaimResponse;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;

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

    public void submit(MissionReadyPayload payload) {
        if (payload == null || payload.getExecutionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid mission.ready payload"
            );
        }

        UUID executionId = payload.getExecutionId();

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


                    if (!executionTracker.complete(executionId)) {
                        throw new IllegalStateException(
                                "Cannot complete mission execution: " + executionId
                        );
                    }

                    payloadStore.remove(executionId);


                    log.info(
                            "Mission simulation completed: executionId={}",
                            executionId
                    );

                } catch (Exception e) {
                    // Mark processing failure as FAILED.
                    // Preserve RESULTS_RETRY for the retry scheduler.
                    executionTracker.fail(executionId);

                    if (executionTracker.getStatus(executionId)
                            == MissionExecutionTracker.Status.RESULTS_RETRY) {

                        if (payloadStore.get(executionId) != null) {
                            retryScheduler.schedule(executionId);
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
            executionTracker.releaseBeforeStart(executionId);

            log.error(
                    "Failed to submit mission execution: executionId={}",
                    executionId,
                    e
            );

            throw e;
        }

    }

    private void submitResultsRetry(UUID executionId) {
        try {
            simulationExecutor.execute(() -> {
                try {
                    missionResultSimulationService.retryResults(executionId);

                    if (!executionTracker.complete(executionId)) {
                        throw new IllegalStateException(
                                "Cannot complete Results retry: " + executionId
                        );
                    }

                    payloadStore.remove(executionId);

                    log.info(
                            "Mission Results retry completed: executionId={}",
                            executionId
                    );

                } catch (Exception e) {
                    executionTracker.markResultsRetry(executionId);

                    log.error(
                            "Mission Results retry failed: executionId={}",
                            executionId,
                            e
                    );
                }
            });

        } catch (RuntimeException e) {
            executionTracker.markResultsRetry(executionId);
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
                || currentStatus == MissionExecutionTracker.Status.COMPLETED) {

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
                    claimed = true;

                    log.info(
                            "Recovery claimed: missionId={}, executionId={}, generation={}, leaseUntil={}",
                            missionId,
                            executionId,
                            claim.recoveryGeneration(),
                            claim.leaseUntil()
                    );

                    // 3. Resume từ waypoint chưa hoàn thành.
                    missionExecutionService.resumeMission(payload);

                    // 4. Xác nhận tracker hoàn thành.
                    if (!executionTracker.complete(executionId)) {
                        throw new IllegalStateException(
                                "Cannot complete recovered execution: "
                                        + executionId
                        );
                    }

                    // 5. Chỉ xóa payload và lease
                    // sau khi Results đã được xác nhận.
                    payloadStore.remove(executionId);
                    recoveryLeaseManager.remove(executionId);
                    executionTracker.clearRecoveryTracking(executionId);

                    log.info(
                            "Mission recovery completed: executionId={}",
                            executionId
                    );


                } catch (RejectedExecutionException e) {

                    // Khôi phục trạng thái trước khi bắt đầu Recovery.
                    boolean rolledBack =
                            executionTracker.rollbackRecovery(executionId);
                    leaseRegistry.cancelBeforeClaim(executionId);

                    // Ngăn execution này gửi request khi chưa có lease.
                    leaseRegistry.markLost(executionId);

                    log.error(
                            "Recovery task rejected: executionId={}, rolledBack={}",
                            executionId,
                            rolledBack,
                            e
                    );

                    throw e;
                }

                catch (Exception e) {

                    executionTracker.fail(executionId);
                    if (!claimed) {
                        leaseRegistry.cancelBeforeClaim(executionId);
                    }

                    boolean ownershipLost =
                            claimed
                                    && !recoveryLeaseManager.hasValidLease(executionId);

                    if (ownershipLost) {
                        recoveryLeaseManager.markLost(executionId);

                        log.error(
                                "Recovery ownership lost: executionId={}",
                                executionId,
                                e
                        );

                        return;
                    }

                    if (executionTracker.getStatus(executionId)
                            == MissionExecutionTracker.Status.RESULTS_RETRY) {

                        if (payloadStore.get(executionId) != null) {
                            retryScheduler.schedule(executionId);
                        } else {
                            log.error(
                                    "Recovery Results payload missing: executionId={}",
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
                finally {

                    executionTracker.clearRecoveryTracking(executionId);

                }
            });

            return true;

        } catch (java.util.concurrent.RejectedExecutionException e) {

            executionTracker.rollbackRecovery(executionId);
            leaseRegistry.cancelBeforeClaim(executionId);

            log.error(
                    "Recovery task rejected: executionId={}",
                    executionId,
                    e
            );

            throw e;
        }
    }


}
