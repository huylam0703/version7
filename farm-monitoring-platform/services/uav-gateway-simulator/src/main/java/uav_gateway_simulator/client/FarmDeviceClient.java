package uav_gateway_simulator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.response.SensorNodeConnectionResponse;
import uav_gateway_simulator.service.TokenService;

import java.util.UUID;

@Component
public class FarmDeviceClient {

    private final RestClient restClient;

    public FarmDeviceClient(
            RestClient.Builder builder,
            TokenService tokenService,
            @Value("${integration.farm-device.base-url}") String baseUrl
    ) {
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(
                            tokenService.getAccessToken()
                    );
                    return execution.execute(request, body);
                })
                .build();
    }

    public SensorNodeConnectionResponse getNodeConnection(UUID nodeId) {
        SensorNodeConnectionResponse response = restClient.get()
                .uri("/api/v1/nodes/{nodeId}/connection", nodeId)
                .retrieve()
                .body(SensorNodeConnectionResponse.class);

        if (response == null) {
            throw new IllegalStateException(
                    "Farm Device returned empty connection response"
            );
        }

        if (!nodeId.equals(response.nodeId())) {
            throw new IllegalStateException("Node ID mismatch");
        }

        if (!"ACTIVE".equalsIgnoreCase(response.status())) {
            throw new IllegalStateException("Sensor Node is not ACTIVE");
        }

        if (!"HTTP".equalsIgnoreCase(response.connectionType())) {
            throw new IllegalStateException("Unsupported connection type");
        }

        if (response.endpointUrl() == null
                || response.endpointUrl().isBlank()) {
            throw new IllegalStateException("Node endpoint is missing");
        }

        return response;
    }
}