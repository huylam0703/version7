package uav_gateway_simulator.dto.request;

import java.time.LocalDateTime;

public record SensorReadingRequest(
        String nodeId,
        String sensorType,
        Double sensorValue,
        LocalDateTime measuredAt,
        LocalDateTime collectedAt,
        Double latitude,
        Double longitude
) {
}
