
package uav_gateway_simulator.dto.request;

import java.time.LocalDateTime;

public record SensorReadingRequest(
        String nodeId,
        String sensorType,
        Double sensorValue,
        LocalDateTime measuredAt,
        LocalDateTime collectedAt,
        Double latitude,
        Double longitude,
        String unit,
        String valueKind,
        Integer adcResolutionBits
) {

    // Backward compatibility for existing tests
    public SensorReadingRequest(
            String nodeId,
            String sensorType,
            Double sensorValue,
            LocalDateTime measuredAt,
            LocalDateTime collectedAt,
            Double latitude,
            Double longitude
    ) {
        this(
                nodeId,
                sensorType,
                sensorValue,
                measuredAt,
                collectedAt,
                latitude,
                longitude,
                null,
                null,
                null
        );
    }
}
