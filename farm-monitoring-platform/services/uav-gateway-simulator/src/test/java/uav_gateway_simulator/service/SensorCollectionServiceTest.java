
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import uav_gateway_simulator.client.SensorDataClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorCollectionServiceTest {

    @Mock
    private SensorDataClient sensorDataClient;

    private SensorCollectionService service;

    @BeforeEach
    void setUp() {
        service = new SensorCollectionService(sensorDataClient);
    }

    @Test
    void shouldCollectAndSendReading() {
        LocalDateTime now = LocalDateTime.now();

        SensorReadingRequest reading =
                new SensorReadingRequest(
                        "44444444-4444-4444-8444-444444444444",
                        "TEMPERATURE",
                        28.5,
                        now,
                        now,
                        10.762622,
                        106.660172
                );

        Map<String, Object> expected =
                Map.of(
                        "status", "VALID",
                        "message", "Sensor reading saved",
                        "id", 1
                );

        when(sensorDataClient.sendReading(reading))
                .thenReturn(expected);

        Map<String, Object> result =
                service.collectAndSend(reading);

        assertEquals("VALID", result.get("status"));
        assertEquals(1, result.get("id"));

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }

    @Test
    void shouldRejectNullReading() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.collectAndSend(null)
        );

        verifyNoInteractions(sensorDataClient);
    }

    @Test
    void shouldRejectMissingNodeId() {
        LocalDateTime now = LocalDateTime.now();

        SensorReadingRequest reading =
                new SensorReadingRequest(
                        "",
                        "TEMPERATURE",
                        28.5,
                        now,
                        now,
                        10.762622,
                        106.660172
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.collectAndSend(reading)
        );

        verifyNoInteractions(sensorDataClient);
    }
}
