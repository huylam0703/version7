package vn.farm.mission.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.farm.core.FarmEvents;
import vn.farm.mission.dto.event.MissionReadyPayload;
import vn.farm.mission.entity.*;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.MissionExecutionRepository;
import vn.farm.mission.repository.MissionRepository;
import vn.farm.mission.repository.MissionStatusLogRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionAckRetryService {

    private final MissionExecutionRepository executionRepository;
    private final MissionRepository missionRepository;
    private final MissionStatusLogRepository statusLogRepository;
    private final MissionOutboxService outboxService;

    @Transactional
    public void processExecution(UUID executionId) {

        MissionExecution execution = executionRepository
                .findByIdAndStatusForUpdate(
                        executionId,
                        MissionExecutionStatus.WAITING_ACK
                )
                .orElse(null);

        if (execution == null) {
            return;
        }
        Mission mission = execution.getMission();

        if (mission.getStatus() != MissionStatus.RUNNING) {
            log.info(
                    "Skip ACK retry: Mission {} is {}",
                    mission.getMissionId(),
                    mission.getStatus()
            );
            return;
        }

        Instant now = Instant.now();
        Instant deadline = execution.getAckDeadline();

        if (deadline == null) {
            log.warn("Execution {} has no ACK deadline", executionId);
            return;
        }

        // Ưu tiên timeout trước retry
        if (!now.isBefore(deadline)) {
            failExecution(execution, now);
            return;
        }

        Instant startedAt = execution.getStartedAt();

        if (startedAt == null) {
            log.warn("Execution {} has no startedAt", executionId);
            return;
        }

        long elapsedSeconds = Duration.between(startedAt, now).getSeconds();
        int attempts = execution.getNotificationAttempts();

        boolean shouldRetry =
                (elapsedSeconds >= 30 && attempts == 1)
                        || (elapsedSeconds >= 60 && attempts == 2);

        if (!shouldRetry) {
            return;
        }

        MissionReadyPayload payload = new MissionReadyPayload(
                execution.getExecutionId(),
                mission.getMissionId(),
                mission.getFarmId(),
                mission.getUavId(),
                mission.getGatewayId(),
                deadline
        );

        outboxService.saveEvent(FarmEvents.MISSION_READY, payload);

        execution.setNotificationAttempts(attempts + 1);
        executionRepository.save(execution);

        log.info(
                "ACK retry queued: executionId={}, attempts={}",
                executionId,
                execution.getNotificationAttempts()
        );
    }

    private void failExecution(
            MissionExecution execution,
            Instant now
    ) {
        Mission mission = execution.getMission();

        execution.setStatus(MissionExecutionStatus.FAILED);
        executionRepository.save(execution);

        if (mission.getStatus() == MissionStatus.RUNNING) {
            MissionStatus oldStatus = mission.getStatus();

            mission.setStatus(MissionStatus.FAILED);
            mission.setEndedAt(now);
            missionRepository.save(mission);

            MissionStatusLog statusLog = MissionStatusLog.builder()
                    .mission(mission)
                    .oldStatus(oldStatus.name())
                    .newStatus(MissionStatus.FAILED.name())
                    .changedBy(mission.getCreatedBy())
                    .source("SYSTEM")
                    .note("Mission ACK timeout after 90 seconds")
                    .createdAt(now)
                    .build();

            statusLogRepository.save(statusLog);

            MissionStatusChangedPayload payload =
                    new MissionStatusChangedPayload();

            payload.setMissionId(mission.getMissionId());
            payload.setFarmId(mission.getFarmId());
            payload.setUavId(mission.getUavId());
            payload.setGatewayId(mission.getGatewayId());

            payload.setOldStatus(
                    vn.farm.contracts.events.missionstatuschanged.MissionStatus.RUNNING
            );

            payload.setNewStatus(
                    vn.farm.contracts.events.missionstatuschanged.MissionStatus.FAILED
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
        }

        log.warn(
                "Mission ACK timeout: executionId={}, missionId={}",
                execution.getExecutionId(),
                mission.getMissionId()
        );
    }
}