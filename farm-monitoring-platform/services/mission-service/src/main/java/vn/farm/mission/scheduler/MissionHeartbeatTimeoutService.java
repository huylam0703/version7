
package vn.farm.mission.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
import vn.farm.core.FarmEvents;
import vn.farm.mission.entity.*;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.MissionExecutionRepository;
import vn.farm.mission.repository.MissionRepository;
import vn.farm.mission.service.MissionExecutionLockService;
import vn.farm.mission.service.MissionService;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionHeartbeatTimeoutService {

    private static final long TIMEOUT_SECONDS = 150;

    private final MissionExecutionRepository executionRepository;
    private final MissionRepository missionRepository;
    private final MissionExecutionLockService lockService;
    private final MissionService missionService;
    private final MissionOutboxService outboxService;

    @Transactional
    public void processExecution(UUID executionId) {

        // Chỉ đọc để xác định Mission cần khóa.
        MissionExecution candidate = executionRepository
                .findById(executionId)
                .orElse(null);

        if (candidate == null) {
            return;
        }

        UUID missionId = candidate.getMission().getMissionId();

        // Khóa Mission trước, Execution sau.
        MissionExecution execution =
                lockService.lock(missionId, executionId);

        Mission mission = execution.getMission();

        if (mission.getStatus() != MissionStatus.RUNNING
                || execution.getStatus()
                != MissionExecutionStatus.EXECUTING) {
            return;
        }

        Instant now = Instant.now();

        Instant lastActivityAt = execution.getLastHeartbeatAt();

        if (lastActivityAt == null) {
            lastActivityAt = execution.getAcknowledgedAt();
        }

        if (lastActivityAt == null) {
            lastActivityAt = execution.getStartedAt();
        }

        if (lastActivityAt == null
                || now.isBefore(
                lastActivityAt.plusSeconds(TIMEOUT_SECONDS))) {
            return;
        }

        // Chỉ xử lý khi đã quá hạn.
        MissionStatus oldStatus = mission.getStatus();

        execution.setStatus(MissionExecutionStatus.FAILED);
        execution.setRecoveryOwnerId(null);
        execution.setRecoveryLeaseUntil(null);

        mission.setStatus(MissionStatus.FAILED);
        mission.setEndedAt(now);

        executionRepository.save(execution);
        missionRepository.save(mission);

        missionService.createMissionStatusLog(
                mission,
                oldStatus.name(),
                MissionStatus.FAILED.name(),
                mission.getCreatedBy(),
                "SYSTEM",
                "Mission heartbeat timeout after 150 seconds"
        );

        MissionStatusChangedPayload payload =
                new MissionStatusChangedPayload();

        payload.setMissionId(mission.getMissionId());
        payload.setFarmId(mission.getFarmId());
        payload.setUavId(mission.getUavId());
        payload.setGatewayId(mission.getGatewayId());

        payload.setOldStatus(
                vn.farm.contracts.events.missionstatuschanged
                        .MissionStatus.fromValue(oldStatus.name())
        );

        payload.setNewStatus(
                vn.farm.contracts.events.missionstatuschanged
                        .MissionStatus.FAILED
        );

        payload.setTotalNodes(mission.getTotalNodes());
        payload.setSuccessNodes(mission.getSuccessNodes());
        payload.setFailedNodes(mission.getFailedNodes());
        payload.setChangedAt(
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        );

        outboxService.saveEvent(
                FarmEvents.MISSION_STATUS_CHANGED,
                payload
        );

        log.warn(
                "Mission heartbeat timeout: missionId={}, executionId={}",
                missionId,
                executionId
        );
    }
}
