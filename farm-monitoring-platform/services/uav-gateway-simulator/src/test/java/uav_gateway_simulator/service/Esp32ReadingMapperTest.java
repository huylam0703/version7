
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uav_gateway_simulator.dto.request.SensorReadingRequest;
import uav_gateway_simulator.dto.response.WaypointResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Esp32ReadingMapperTest {

    private Esp32ReadingMapper mapper;
    private ObjectMapper objectMapper;
    private WaypointResponse waypoint;
    private UUID nodeId;

    @BeforeEach
    void setUp() {
        mapper = new Esp32ReadingMapper();
        objectMapper = new ObjectMapper();

        nodeId = UUID.randomUUID();

        waypoint = mock(WaypointResponse.class);

        when(waypoint.lat())
                .thenReturn(new BigDecimal("10.762622"));

        when(waypoint.lng())
                .thenReturn(new BigDecimal("106.660172"));
    }

    // TC01: NODE_1 returns three physical readings
    @Test
    void shouldMapNode1Readings() {
        JsonNode data = objectMapper.readTree("""
                {
                    "node": "NODE_1",
                    "temperature": 33.2,
                    "humidity": 65.9,
                    "light": 120.5
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        assertEquals(3, readings.size());

        assertReading(
                readings.get(0),
                "AIR_TEMP", 33.2,
                "°C", "PHYSICAL", null
        );

        assertReading(
                readings.get(1),
                "AIR_HUMIDITY", 65.9,
                "%", "PHYSICAL", null
        );

        assertReading(
                readings.get(2),
                "LIGHT", 120.5,
                "lux", "PHYSICAL", null
        );
    }

    // TC02: NODE_2 returns two raw ADC readings
    @Test
    void shouldMapNode2Readings() {
        JsonNode data = objectMapper.readTree("""
                {
                    "node": "NODE_2",
                    "waterLevelRaw": 0,
                    "soilMoistureRaw": 2425
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        assertEquals(2, readings.size());

        assertReading(
                readings.get(0),
                "WATER_LEVEL", 0.0,
                "ADC_COUNT", "ADC_RAW", 12
        );

        assertReading(
                readings.get(1),
                "SOIL_MOISTURE", 2425.0,
                "ADC_COUNT", "ADC_RAW", 12
        );
    }

    // TC03: Zero lux is a valid reading
    @Test
    void shouldAcceptZeroLightValue() {
        JsonNode data = objectMapper.readTree("""
                {
                    "light": 0
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        assertEquals(1, readings.size());

        assertReading(
                readings.getFirst(),
                "LIGHT", 0.0,
                "lux", "PHYSICAL", null
        );
    }

    // TC04: Maximum 12-bit ADC value is valid
    @Test
    void shouldAcceptMaximumAdcValue() {
        JsonNode data = objectMapper.readTree("""
                {
                    "soilMoistureRaw": 4095
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        assertEquals(1, readings.size());
        assertEquals(
                4095.0,
                readings.getFirst().sensorValue()
        );
    }

    // TC05: ADC value exceeds 12-bit range
    @Test
    void shouldRejectAdcValueAboveMaximum() {
        JsonNode data = objectMapper.readTree("""
                {
                    "soilMoistureRaw": 4096
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC06: Negative ADC value
    @Test
    void shouldRejectNegativeAdcValue() {
        JsonNode data = objectMapper.readTree("""
                {
                    "waterLevelRaw": -1
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC07: ADC count must be an integer
    @Test
    void shouldRejectDecimalAdcValue() {
        JsonNode data = objectMapper.readTree("""
                {
                    "soilMoistureRaw": 2425.5
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC08: Sensor field must be numeric
    @Test
    void shouldRejectNonNumericSensorValue() {
        JsonNode data = objectMapper.readTree("""
                {
                    "temperature": "invalid"
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC09: No supported sensor fields
    @Test
    void shouldRejectUnsupportedSensorData() {
        JsonNode data = objectMapper.readTree("""
                {
                    "node": "NODE_1",
                    "unknownSensor": 100
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC10: Null ESP32 data
    @Test
    void shouldRejectNullData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, null, waypoint)
        );
    }

    // TC11: Missing node ID
    @Test
    void shouldRejectNullNodeId() {
        JsonNode data = objectMapper.readTree("""
                {
                    "temperature": 30
                }
                """);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(null, data, waypoint)
        );
    }

    // TC12: Missing waypoint coordinates
    @Test
    void shouldRejectMissingCoordinates() {
        JsonNode data = objectMapper.readTree("""
                {
                    "temperature": 30
                }
                """);

        when(waypoint.lat()).thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.map(nodeId, data, waypoint)
        );
    }

    // TC13: Null sensor field is ignored
    @Test
    void shouldIgnoreNullSensorFields() {
        JsonNode data = objectMapper.readTree("""
                {
                    "temperature": 33.2,
                    "humidity": null
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        assertEquals(1, readings.size());

        assertEquals(
                "AIR_TEMP",
                readings.getFirst().sensorType()
        );
    }

    // TC14: Verify node ID, coordinates and timestamps
    @Test
    void shouldMapReadingMetadataCorrectly() {
        JsonNode data = objectMapper.readTree("""
                {
                    "temperature": 33.2,
                    "humidity": 65.9,
                    "light": 120.5
                }
                """);

        List<SensorReadingRequest> readings =
                mapper.map(nodeId, data, waypoint);

        for (SensorReadingRequest reading : readings) {
            assertEquals(
                    nodeId.toString(),
                    reading.nodeId()
            );

            assertEquals(
                    10.762622,
                    reading.latitude(),
                    0.000001
            );

            assertEquals(
                    106.660172,
                    reading.longitude(),
                    0.000001
            );

            assertNotNull(reading.measuredAt());
            assertNotNull(reading.collectedAt());

            assertEquals(
                    reading.measuredAt(),
                    reading.collectedAt()
            );

            assertEquals(
                    readings.getFirst().measuredAt(),
                    reading.measuredAt()
            );

            // Timestamp precision: milliseconds
            assertEquals(
                    0,
                    reading.measuredAt().getNano() % 1_000_000
            );
        }
    }

    private void assertReading(
            SensorReadingRequest reading,
            String sensorType,
            double sensorValue,
            String unit,
            String valueKind,
            Integer adcResolutionBits
    ) {
        assertEquals(sensorType, reading.sensorType());

        assertEquals(
                sensorValue,
                reading.sensorValue(),
                0.000001
        );

        assertEquals(unit, reading.unit());
        assertEquals(valueKind, reading.valueKind());

        assertEquals(
                adcResolutionBits,
                reading.adcResolutionBits()
        );
    }
}
