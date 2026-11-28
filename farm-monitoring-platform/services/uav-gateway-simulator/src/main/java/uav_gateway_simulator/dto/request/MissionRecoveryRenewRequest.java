package uav_gateway_simulator.dto.request;

import java.util.UUID;

public record MissionRecoveryRenewRequest(
        UUID executionId,
        UUID instanceId,
        long recoveryGeneration
) {
}