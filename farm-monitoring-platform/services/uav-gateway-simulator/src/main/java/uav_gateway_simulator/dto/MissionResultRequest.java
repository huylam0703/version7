package uav_gateway_simulator.dto;

import java.util.List;
import java.util.UUID;

public record MissionResultRequest(
        UUID executionId,
        List<MissionNodeResultRequest> results,
        Boolean completed
) {
}