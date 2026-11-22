package uav_gateway_simulator.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MissionAckResponse(
        UUID missionId,
        UUID executionId,
        String status,
        Instant acknowledgedAt
) {
}