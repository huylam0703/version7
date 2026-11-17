package uav_gateway_simulator.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MissionProgressRequest(
        UUID executionId,
        long sequence,
        Short waypointSeq,
        Instant reachedAt,
        List<Object> nodeResults
) {
}