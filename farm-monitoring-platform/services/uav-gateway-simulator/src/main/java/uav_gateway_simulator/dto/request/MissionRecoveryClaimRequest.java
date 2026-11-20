package uav_gateway_simulator.dto.request;

import java.util.UUID;

public record MissionRecoveryClaimRequest(
        UUID executionId,
        UUID instanceId
) {
}