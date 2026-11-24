
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import uav_gateway_simulator.client.SensorDataClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorCollectionService {

    private static final int MAX_ATTEMPTS = 3;

    private final SensorDataClient sensorDataClient;

    public Map<String, Object> collectAndSend(
            SensorReadingRequest reading
    ) {
        validateReading(reading);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Map<String, Object> response =
                        sensorDataClient.sendReading(reading);

                if (response == null) {
                    throw new IllegalStateException(
                            "Empty Sensor Data response"
                    );
                }

                String status = String.valueOf(
                        response.get("status")
                );

                switch (status) {
                    case "VALID", "DUPLICATE" -> {
                        log.info(
                                "Sensor upload accepted: nodeId={}, type={}, status={}, attempt={}",
                                reading.nodeId(),
                                reading.sensorType(),
                                status,
                                attempt
                        );
                        return response;
                    }
                    case "INVALID" -> throw new IllegalStateException(
                            "Sensor reading rejected as INVALID"
                    );
                    default -> throw new IllegalStateException(
                            "Unexpected Sensor Data status: " + status
                    );
                }

            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();

                boolean retryable =
                        status.value() == 429 ||
                                status.is5xxServerError();

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                log.warn(
                        "Sensor upload HTTP error: status={}, attempt={}/{}",
                        status.value(),
                        attempt,
                        MAX_ATTEMPTS
                );

            } catch (RestClientException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                log.warn(
                        "Sensor upload connection error: attempt={}/{}, error={}",
                        attempt,
                        MAX_ATTEMPTS,
                        e.getMessage()
                );
            }

            try {
                Thread.sleep(300L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Sensor upload interrupted", e
                );
            }
        }

        throw new IllegalStateException(
                "Sensor upload attempts exhausted"
        );
    }

    private void validateReading(SensorReadingRequest reading) {
        if (reading == null) {
            throw new IllegalArgumentException(
                    "Sensor reading must not be null"
            );
        }

        if (reading.nodeId() == null ||
                reading.nodeId().isBlank()) {
            throw new IllegalArgumentException(
                    "Node ID is required"
            );
        }

        if (reading.sensorType() == null ||
                reading.sensorType().isBlank()) {
            throw new IllegalArgumentException(
                    "Sensor type is required"
            );
        }

        if (reading.sensorValue() == null ||
                !Double.isFinite(reading.sensorValue())) {
            throw new IllegalArgumentException(
                    "Sensor value must be finite"
            );
        }

        if (reading.measuredAt() == null ||
                reading.collectedAt() == null) {
            throw new IllegalArgumentException(
                    "Sensor timestamps are required"
            );
        }

        if (reading.latitude() == null ||
                !Double.isFinite(reading.latitude()) ||
                reading.latitude() < -90 ||
                reading.latitude() > 90 ||
                reading.longitude() == null ||
                !Double.isFinite(reading.longitude()) ||
                reading.longitude() < -180 ||
                reading.longitude() > 180) {
            throw new IllegalArgumentException(
                    "Invalid sensor coordinates"
            );
        }
    }
}
