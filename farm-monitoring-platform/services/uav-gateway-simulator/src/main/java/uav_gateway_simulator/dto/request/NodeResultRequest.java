
package uav_gateway_simulator.dto.request;

import java.time.Instant;
import java.util.UUID;

public record NodeResultRequest(
        UUID nodeId,
        String status,
        Instant collectedAt,
        String errorCode,
        String errorMessage
) {
}
