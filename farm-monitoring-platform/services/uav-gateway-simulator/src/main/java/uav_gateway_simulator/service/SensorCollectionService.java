
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.SensorDataClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SensorCollectionService {

    private final SensorDataClient sensorDataClient;

    public Map<String, Object> collectAndSend(
            SensorReadingRequest reading
    ) {
        validateReading(reading);

        return sensorDataClient.sendReading(reading);
    }

    private void validateReading(
            SensorReadingRequest reading
    ) {
        if (reading == null) {
            throw new IllegalArgumentException(
                    "Sensor reading must not be null"
            );
        }

        if (reading.nodeId() == null
                || reading.nodeId().isBlank()) {
            throw new IllegalArgumentException(
                    "Node ID is required"
            );
        }

        if (reading.sensorType() == null
                || reading.sensorType().isBlank()) {
            throw new IllegalArgumentException(
                    "Sensor type is required"
            );
        }

        if (reading.sensorValue() == null
                || !Double.isFinite(reading.sensorValue())) {
            throw new IllegalArgumentException(
                    "Sensor value must be finite"
            );
        }

        if (reading.measuredAt() == null
                || reading.collectedAt() == null) {
            throw new IllegalArgumentException(
                    "Sensor timestamps are required"
            );
        }
    }
}
