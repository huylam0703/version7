
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.RecoverableMissionResponse;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionRecoveryCoordinator {

    private final MissionClient missionClient;
    private final MissionProcessingService missionProcessingService;
    private final MissionExecutionTracker executionTracker;

    public void recoverAvailableMissions() {

        log.info("Scanning for recoverable missions...");

        List<RecoverableMissionResponse> missions =
                missionClient.getRecoverableMissions();

        log.info(
                "Found {} recoverable missions",
                missions.size()
        );

        for (RecoverableMissionResponse mission : missions) {

            try {
                recoverMission(mission);

            } catch (Exception e) {
                log.error(
                        "Failed to start recovery: missionId={}, executionId={}",
                        mission.missionId(),
                        mission.executionId(),
                        e
                );
            }
        }

        log.info("Recoverable missions scan completed");
    }

    private void recoverMission(RecoverableMissionResponse mission) {

        if (mission == null
                || mission.missionId() == null
                || mission.executionId() == null
                || mission.farmId() == null
                || mission.uavId() == null
                || mission.gatewayId() == null) {

            log.warn("Skipping invalid recoverable mission");
            return;
        }

        UUID executionId = mission.executionId();

        MissionExecutionTracker.Status status =
                executionTracker.getStatus(executionId);

        if (status == MissionExecutionTracker.Status.PROCESSING
                || status == MissionExecutionTracker.Status.RESULTS_PENDING
                || status == MissionExecutionTracker.Status.RESULTS_RETRY
                || status == MissionExecutionTracker.Status.COMPLETED) {

            log.info(
                    "Skipping recovery: executionId={}, status={}",
                    executionId,
                    status
            );

            return;
        }

        MissionReadyPayload payload = new MissionReadyPayload();

        payload.setMissionId(mission.missionId());
        payload.setExecutionId(mission.executionId());
        payload.setFarmId(mission.farmId());
        payload.setUavId(mission.uavId());
        payload.setGatewayId(mission.gatewayId());

        boolean accepted = missionProcessingService.recover(payload);

        if (accepted) {
            log.info(
                    "Recovery submitted: missionId={}, executionId={}",
                    mission.missionId(),
                    executionId
            );
        } else {
            log.info(
                    "Recovery skipped by processing service: executionId={}",
                    executionId
            );
        }
    }
}
