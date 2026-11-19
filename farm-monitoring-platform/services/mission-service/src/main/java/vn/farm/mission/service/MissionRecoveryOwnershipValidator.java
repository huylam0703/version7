package vn.farm.mission.service;

import org.springframework.stereotype.Component;
import vn.farm.core.BusinessException;
import vn.farm.mission.entity.MissionExecution;

import java.time.Instant;
import java.util.UUID;

@Component
public class MissionRecoveryOwnershipValidator {

    public void validate(
            MissionExecution execution,
            UUID instanceId,
            Long recoveryGeneration
    ) {
        if (execution.getRecoveryOwnerId() == null) {
            return;
        }

        if (instanceId == null
                || recoveryGeneration == null
                || !execution.getRecoveryOwnerId()
                .equals(instanceId.toString())
                || execution.getRecoveryGeneration()
                != recoveryGeneration
                || execution.getRecoveryLeaseUntil() == null
                || !execution.getRecoveryLeaseUntil()
                .isAfter(Instant.now())) {

            throw BusinessException.invalid(
                    "RECOVERY_OWNERSHIP_MISMATCH",
                    "Simulator không còn quyền cập nhật Mission Execution"
            );
        }
    }
}