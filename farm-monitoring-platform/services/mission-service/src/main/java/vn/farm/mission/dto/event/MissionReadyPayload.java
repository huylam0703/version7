package vn.farm.mission.dto.event;

import java.time.Instant;
import java.util.UUID;

public record MissionReadyPayload(
        UUID executionId,
        UUID missionId,
        UUID farmId,
        UUID uavId,
        UUID gatewayId,
        Instant ackDeadline
) {
}