
package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import uav_gateway_simulator.dto.request.SensorReadingRequest;
import uav_gateway_simulator.dto.response.WaypointResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class Esp32ReadingMapper {

    private static final int ADC_RESOLUTION_BITS = 12;

    private static final String PHYSICAL = "PHYSICAL";
    private static final String ADC_RAW = "ADC_RAW";
    private static final String ADC_COUNT = "ADC_COUNT";

    public List<SensorReadingRequest> map(
            UUID nodeId,
            JsonNode data,
            WaypointResponse waypoint
    ) {
        if (nodeId == null || data == null || !data.isObject()) {
            throw new IllegalArgumentException(
                    "Invalid ESP32 data"
            );
        }

        if (waypoint == null
                || waypoint.lat() == null
                || waypoint.lng() == null) {
            throw new IllegalArgumentException(
                    "Waypoint coordinates are required"
            );
        }

        LocalDateTime measuredAt = LocalDateTime
                .now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS);

        LocalDateTime collectedAt = measuredAt;

        List<SensorReadingRequest> readings = new ArrayList<>();

        // NODE_1 - Air temperature
        addReading(
                readings, nodeId, data,
                "temperature", "AIR_TEMP",
                "°C", PHYSICAL, null,
                measuredAt, collectedAt, waypoint
        );

        // NODE_1 - Air humidity
        addReading(
                readings, nodeId, data,
                "humidity", "AIR_HUMIDITY",
                "%", PHYSICAL, null,
                measuredAt, collectedAt, waypoint
        );

        // NODE_1 - Light intensity
        addReading(
                readings, nodeId, data,
                "light", "LIGHT",
                "lux", PHYSICAL, null,
                measuredAt, collectedAt, waypoint
        );

        // NODE_2 - Raw water level
        addReading(
                readings, nodeId, data,
                "waterLevelRaw", "WATER_LEVEL",
                ADC_COUNT, ADC_RAW, ADC_RESOLUTION_BITS,
                measuredAt, collectedAt, waypoint
        );

        // NODE_2 - Raw soil moisture
        addReading(
                readings, nodeId, data,
                "soilMoistureRaw", "SOIL_MOISTURE",
                ADC_COUNT, ADC_RAW, ADC_RESOLUTION_BITS,
                measuredAt, collectedAt, waypoint
        );

        if (readings.isEmpty()) {
            throw new IllegalArgumentException(
                    "No supported sensor readings found"
            );
        }

        return readings;
    }

    private void addReading(
            List<SensorReadingRequest> readings,
            UUID nodeId,
            JsonNode data,
            String jsonField,
            String sensorType,
            String unit,
            String valueKind,
            Integer adcResolutionBits,
            LocalDateTime measuredAt,
            LocalDateTime collectedAt,
            WaypointResponse waypoint
    ) {
        JsonNode value = data.get(jsonField);

        if (value == null || value.isNull()) {
            return;
        }

        if (!value.isNumber()) {
            throw new IllegalArgumentException(
                    "Sensor field must be numeric: " + jsonField
            );
        }

        double sensorValue = value.doubleValue();

        if (!Double.isFinite(sensorValue)) {
            throw new IllegalArgumentException(
                    "Invalid sensor value: " + jsonField
            );
        }

        // Validate raw ADC count
        if (ADC_RAW.equals(valueKind)) {
            long maxAdcValue = (1L << adcResolutionBits) - 1;

            if (sensorValue < 0
                    || sensorValue > maxAdcValue
                    || sensorValue != Math.floor(sensorValue)) {
                throw new IllegalArgumentException(
                        "Invalid ADC raw value: " + jsonField
                );
            }
        }

        readings.add(new SensorReadingRequest(
                nodeId.toString(),
                sensorType,
                sensorValue,
                measuredAt,
                collectedAt,
                waypoint.lat().doubleValue(),
                waypoint.lng().doubleValue(),
                unit,
                valueKind,
                adcResolutionBits
        ));
    }
}
