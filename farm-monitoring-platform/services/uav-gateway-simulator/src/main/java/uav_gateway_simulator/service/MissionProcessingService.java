
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.dto.MissionReadyPayload;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

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

                    executionTracker.complete(executionId);
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

        UUID executionId = payload.getExecutionId();


        MissionExecutionTracker.Status currentStatus =
                executionTracker.getStatus(executionId);

        if (currentStatus
                == MissionExecutionTracker.Status.RESULTS_PENDING
                || currentStatus
                == MissionExecutionTracker.Status.RESULTS_RETRY) {

            log.warn(
                    "Recovery skipped: Results processing is active, executionId={}, status={}",
                    executionId,
                    currentStatus
            );

            return false;
        }

        if (currentStatus
                == MissionExecutionTracker.Status.COMPLETED) {

            log.info(
                    "Recovery skipped: Execution already completed, executionId={}",
                    executionId
            );

            return false;
        }


        // Only one recovery can start from FAILED.
        if (!executionTracker.tryStartRecovery(executionId)) {
            log.warn(
                    "Recovery rejected: executionId={}, status={}",
                    executionId,
                    executionTracker.getStatus(executionId)
            );
            return false;
        }

        try {
            simulationExecutor.execute(() -> {
                try {
                    missionExecutionService.resumeMission(payload);

                    if (!executionTracker.complete(executionId)) {
                        throw new IllegalStateException(
                                "Cannot complete recovered execution: "
                                        + executionId
                        );
                    }

                    payloadStore.remove(executionId);

                    log.info(
                            "Mission recovery completed: executionId={}",
                            executionId
                    );

                } catch (Exception e) {
                    executionTracker.fail(executionId);

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
            });

            return true;

        } catch (java.util.concurrent.RejectedExecutionException e) {
            executionTracker.rollbackRecovery(executionId);

            log.error(
                    "Recovery task rejected: executionId={}",
                    executionId,
                    e
            );

            throw e;
        }
    }

}
