package uav_gateway_simulator.dto;

import java.time.Instant;
import java.util.UUID;

public record MissionHeartbeatResponse(
        UUID missionId,
        UUID executionId,
        String executionStatus,
        Instant lastHeartbeatAt
) {
}