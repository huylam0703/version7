
package uav_gateway_simulator.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SensorNodeConnectionResponse(
        UUID nodeId,
        String deviceUid,
        String connectionType,
        String endpointUrl,
        String status,
        Instant lastSeenAt
) {
}
