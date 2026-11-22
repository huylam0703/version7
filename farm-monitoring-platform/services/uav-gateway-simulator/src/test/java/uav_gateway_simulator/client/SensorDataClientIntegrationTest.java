
package uav_gateway_simulator.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SensorDataClientIntegrationTest {

    @Test
    void shouldSendSensorReadingSuccessfully() {

        SensorDataClient client = new SensorDataClient(
                RestClient.builder(),
                "http://localhost:8084"
        );

        LocalDateTime now = LocalDateTime.now();

        SensorReadingRequest request = new SensorReadingRequest(
                "44444444-4444-4444-8444-444444444444",
                "TEMPERATURE",
                28.5,
                now,
                now,
                10.762622,
                106.660172
        );

        Map<String, Object> response =
                client.sendReading(request);

        assertNotNull(response);
        assertEquals("VALID", response.get("status"));
        assertEquals("Sensor reading saved", response.get("message"));
        assertNotNull(response.get("id"));
    }
}
