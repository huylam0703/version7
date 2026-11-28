package uav_gateway_simulator.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MissionRecoveryClaimResponse(
        UUID missionId,
        UUID executionId,
        UUID instanceId,
        long recoveryGeneration,
        Instant leaseUntil
) {
}