package uav_gateway_simulator.dto;

import java.util.UUID;

public record MissionResultResponse(
        UUID missionId,
        UUID executionId,
        String status,
        Integer totalNodes,
        Integer successNodes,
        Integer failedNodes
) {
}