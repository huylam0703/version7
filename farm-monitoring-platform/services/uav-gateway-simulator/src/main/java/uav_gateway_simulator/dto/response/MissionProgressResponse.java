package uav_gateway_simulator.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MissionProgressResponse(
        UUID missionId,
        UUID executionId,
        String executionStatus,
        long lastConfirmedSequence,
        Short waypointSeq,
        Instant updatedAt
) {
}