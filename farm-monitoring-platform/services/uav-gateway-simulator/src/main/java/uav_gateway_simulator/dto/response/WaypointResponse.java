package uav_gateway_simulator.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WaypointResponse(
        UUID waypointId,
        int seq,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal altitudeM,
        String status
) {
}