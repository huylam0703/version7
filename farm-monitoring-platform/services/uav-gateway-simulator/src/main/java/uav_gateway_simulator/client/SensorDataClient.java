
package uav_gateway_simulator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.util.Map;

@Component
public class SensorDataClient {

    private final RestClient restClient;

    public SensorDataClient(
            RestClient.Builder builder,
            @Value("${integration.sensor-data.base-url}")
            String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public Map<String, Object> sendReading(
            SensorReadingRequest request
    ) {
        return restClient.post()
                .uri("/api/sensor-readings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
