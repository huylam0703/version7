package uav_gateway_simulator.dto;

import java.time.Instant;
import java.util.UUID;

public record MissionExecutionProgressResponse(
        UUID missionId,
        UUID executionId,
        String executionStatus,
        long lastConfirmedSequence,
        Instant acknowledgedAt,
        Instant lastHeartbeatAt
) {
}
