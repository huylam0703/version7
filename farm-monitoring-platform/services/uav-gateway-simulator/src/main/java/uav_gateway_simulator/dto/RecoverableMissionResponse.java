package uav_gateway_simulator.dto;

import java.util.UUID;

public record RecoverableMissionResponse(
        UUID missionId,
        UUID executionId,
        UUID farmId,
        UUID uavId,
        UUID gatewayId
) {
}