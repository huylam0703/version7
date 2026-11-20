
package uav_gateway_simulator.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MissionProgressDetailResponse(
        UUID missionId,
        UUID executionId,
        String missionStatus,
        String executionStatus,
        long lastConfirmedSequence,
        int totalWaypoints,
        int reachedWaypoints,
        int totalNodes,
        int processedNodes,
        double progressPercent,
        List<WaypointProgress> waypoints,
        List<NodeProgress> nodes
) {
    public record WaypointProgress(
            UUID waypointId,
            short seq,
            String status,
            Instant reachedAt
    ) {}

    public record NodeProgress(
            UUID nodeId,
            String result,
            int recordsCount,
            Integer attempts,
            Instant collectedAt
    ) {}
}
