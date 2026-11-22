package uav_gateway_simulator.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MissionDetailResponse(
        UUID missionId,
        UUID farmId,
        UUID uavId,
        UUID gatewayId,
        String name,
        String createdBy,
        String notes,
        String status,
        Instant plannedAt,
        Instant startedAt,
        Instant endedAt,
        Integer totalNodes,
        Integer successNodes,
        Integer failedNodes,
        List<WaypointResponse> waypoints,
        List<UUID> nodeIds
) {
}