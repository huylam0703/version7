package uav_gateway_simulator.dto.request;

import java.time.Instant;
import java.util.UUID;

public record MissionNodeResultRequest(
        UUID nodeId,
        String result,
        Integer recordsCount,
        Integer attempts,
        Instant collectedAt,
        String errorCode,
        String errorMessage
) {
}