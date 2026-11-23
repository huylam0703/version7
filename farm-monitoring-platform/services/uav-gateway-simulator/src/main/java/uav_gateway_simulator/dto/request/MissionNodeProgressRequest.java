
package uav_gateway_simulator.dto.request;

import java.time.Instant;
import java.util.UUID;

public record MissionNodeProgressRequest(
        UUID nodeId,
        String result,
        int recordsCount,
        int attempts,
        Instant collectedAt
) {
}
