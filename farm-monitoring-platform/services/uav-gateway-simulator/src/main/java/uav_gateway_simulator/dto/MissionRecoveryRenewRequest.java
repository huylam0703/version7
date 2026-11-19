package uav_gateway_simulator.dto;

import java.util.UUID;

public record MissionRecoveryRenewRequest(
        UUID executionId,
        UUID instanceId,
        long recoveryGeneration
) {
}