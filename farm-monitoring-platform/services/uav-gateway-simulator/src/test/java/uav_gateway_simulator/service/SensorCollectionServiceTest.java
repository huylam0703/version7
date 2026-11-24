
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    private SensorReadingRequest reading;

    @BeforeEach
    void setUp() {
        service = new SensorCollectionService(sensorDataClient);

        LocalDateTime now = LocalDateTime.now();

        reading = new SensorReadingRequest(
                "44444444-4444-4444-8444-444444444444",
                "TEMPERATURE",
                28.5,
                now,
                now,
                10.762622,
                106.660172
        );
    }

    @Test
    void shouldCollectAndSendReading() {
        Map<String, Object> expected = Map.of(
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

        SensorReadingRequest invalid = new SensorReadingRequest(
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
                () -> service.collectAndSend(invalid)
        );

        verifyNoInteractions(sensorDataClient);
    }

    @Test
    void shouldAcceptDuplicateWithoutRetry() {
        when(sensorDataClient.sendReading(reading))
                .thenReturn(Map.of(
                        "status", "DUPLICATE",
                        "message", "Sensor reading already exists"
                ));

        Map<String, Object> result =
                service.collectAndSend(reading);

        assertEquals("DUPLICATE", result.get("status"));

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }

    @Test
    void shouldRejectInvalidWithoutRetry() {
        when(sensorDataClient.sendReading(reading))
                .thenReturn(Map.of("status", "INVALID"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.collectAndSend(reading)
        );

        assertTrue(exception.getMessage().contains("INVALID"));

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }

    @Test
    void shouldRetryOnHttp500AndEventuallySucceed() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR
                ))
                .thenReturn(Map.of("status", "VALID"));

        Map<String, Object> result =
                service.collectAndSend(reading);

        assertEquals("VALID", result.get("status"));

        verify(sensorDataClient, times(2))
                .sendReading(reading);
    }

    @Test
    void shouldRetryOnHttp503AndEventuallySucceed() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.SERVICE_UNAVAILABLE
                ))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.SERVICE_UNAVAILABLE
                ))
                .thenReturn(Map.of("status", "VALID"));

        Map<String, Object> result =
                service.collectAndSend(reading);

        assertEquals("VALID", result.get("status"));

        verify(sensorDataClient, times(3))
                .sendReading(reading);
    }

    @Test
    void shouldFailAfterMaxRetries() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new HttpServerErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        assertThrows(
                HttpServerErrorException.class,
                () -> service.collectAndSend(reading)
        );

        verify(sensorDataClient, times(3))
                .sendReading(reading);
    }

    @Test
    void shouldNotRetryOnHttp400() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new HttpClientErrorException(
                        HttpStatus.BAD_REQUEST
                ));

        assertThrows(
                HttpClientErrorException.class,
                () -> service.collectAndSend(reading)
        );

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }

    @Test
    void shouldRetryOnConnectionError() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new ResourceAccessException(
                        "Connection refused"
                ))
                .thenReturn(Map.of("status", "VALID"));

        Map<String, Object> result =
                service.collectAndSend(reading);

        assertEquals("VALID", result.get("status"));

        verify(sensorDataClient, times(2))
                .sendReading(reading);
    }

    @Test
    void shouldFailWhenConnectionNeverRecovers() {
        when(sensorDataClient.sendReading(reading))
                .thenThrow(new ResourceAccessException(
                        "Connection refused"
                ));

        assertThrows(
                ResourceAccessException.class,
                () -> service.collectAndSend(reading)
        );

        verify(sensorDataClient, times(3))
                .sendReading(reading);
    }

    @Test
    void shouldRejectNullResponseWithoutRetry() {
        when(sensorDataClient.sendReading(reading))
                .thenReturn(null);

        assertThrows(
                IllegalStateException.class,
                () -> service.collectAndSend(reading)
        );

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }

    @Test
    void shouldRejectUnexpectedStatusWithoutRetry() {
        when(sensorDataClient.sendReading(reading))
                .thenReturn(Map.of("status", "UNKNOWN"));

        assertThrows(
                IllegalStateException.class,
                () -> service.collectAndSend(reading)
        );

        verify(sensorDataClient, times(1))
                .sendReading(reading);
    }
}
