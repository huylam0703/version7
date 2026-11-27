
package uav_gateway_simulator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.time.ZoneOffset;
import java.util.List;

@Component
public class SensorDataClient {

    private final RestClient restClient;

    public SensorDataClient(
            RestClient.Builder builder,
            @Value("${integration.sensor-data.base-url}")
            String baseUrl
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public SyncResponse sendBatch(List<SensorReadingRequest> readings) {
        var request = new SyncRequest(readings.stream().map(SyncReading::from).toList());
        return restClient.post()
                .uri("/api/v1/sync-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SyncResponse.class);
    }

    private record SyncRequest(List<SyncReading> readings) {}

    private record SyncReading(
            String nodeId, String sensorType, Double sensorValue,
            String measuredAt, String collectedAt, Double latitude, Double longitude,
            String unit, String valueKind, Integer adcResolutionBits
    ) {
        private static SyncReading from(SensorReadingRequest reading) {
            return new SyncReading(
                    reading.nodeId(), reading.sensorType(), reading.sensorValue(),
                    reading.measuredAt().toInstant(ZoneOffset.UTC).toString(),
                    reading.collectedAt().toInstant(ZoneOffset.UTC).toString(),
                    reading.latitude(), reading.longitude(), reading.unit(),
                    reading.valueKind(), reading.adcResolutionBits()
            );
        }
    }

    public record SyncResponse(int total, int validCount, int invalidCount, int duplicateCount,
                               List<SyncItem> items) {}

    public record SyncItem(Integer index, String status, Long id) {}
}
