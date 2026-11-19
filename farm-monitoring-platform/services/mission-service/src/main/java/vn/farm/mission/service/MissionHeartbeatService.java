package vn.farm.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionHeartbeatRequest;
import vn.farm.mission.dto.response.MissionHeartbeatResponse;
import vn.farm.mission.entity.Mission;
import vn.farm.mission.entity.MissionExecution;
import vn.farm.mission.entity.MissionExecutionStatus;
import vn.farm.mission.entity.MissionStatus;
import vn.farm.mission.repository.MissionExecutionRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionHeartbeatService {

    private final MissionExecutionRepository executionRepository;
    private final MissionRecoveryOwnershipValidator ownershipValidator;
    private final MissionExecutionLockService executionLockService;

    @Transactional
    public MissionHeartbeatResponse receiveHeartbeat(
            UUID missionId,
            MissionHeartbeatRequest request,
            String clientId
    ) {

        // 1. Tìm và khóa Mission Execution
        MissionExecution execution = executionLockService.lock(
                missionId,
                request.getExecutionId()
        );

        Mission mission = execution.getMission();

        // 2. Kiểm tra Mission đang RUNNING
        if (mission.getStatus() != MissionStatus.RUNNING) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Mission phải ở trạng thái RUNNING"
            );
        }

        // 3. Kiểm tra Execution đang EXECUTING
        if (execution.getStatus() != MissionExecutionStatus.EXECUTING) {
            throw BusinessException.invalid(
                    "INVALID_EXECUTION_STATUS",
                    "Execution phải ở trạng thái EXECUTING"
            );
        }

        // 4. Kiểm tra UAV Client được phân công
        if (clientId == null
                || clientId.isBlank()
                || !clientId.equals(execution.getAssignedClientId())) {

            throw BusinessException.invalid(
                    "UNAUTHORIZED_UAV_CLIENT",
                    "UAV Client không được phép gửi Heartbeat cho Execution này"
            );
        }
        ownershipValidator.validate(
                execution,
                request.getInstanceId(),
                request.getRecoveryGeneration()
        );

        // 5. Cập nhật thời gian Heartbeat
        Instant now = Instant.now();

        execution.setLastHeartbeatAt(now);

        executionRepository.save(execution);

        // 6. Trả kết quả
        return MissionHeartbeatResponse.builder()
                .missionId(missionId)
                .executionId(execution.getExecutionId())
                .executionStatus(execution.getStatus().name())
                .lastHeartbeatAt(now)
                .build();
    }
}