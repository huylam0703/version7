package vn.farm.mission.dto.response;

import java.util.UUID;

public record RecoverableMissionResponse(
        UUID missionId,
        UUID executionId,
        UUID farmId,
        UUID uavId,
        UUID gatewayId
) {
}