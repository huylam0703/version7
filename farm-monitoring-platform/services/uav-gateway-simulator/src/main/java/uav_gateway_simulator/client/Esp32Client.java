
package uav_gateway_simulator.client;

import tools.jackson.databind.JsonNode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.response.SensorNodeConnectionResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class Esp32Client {

    private final FarmDeviceClient farmDeviceClient;
    private final RestClient restClient;

    public Esp32Client(
            RestClient.Builder builder,
            FarmDeviceClient farmDeviceClient
    ) {
        this.farmDeviceClient = farmDeviceClient;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = builder.clone()
                .requestFactory(requestFactory)
                .build();
    }

    public JsonNode readByNodeId(UUID nodeId) {

        // 1. Tra cứu thông tin kết nối từ Farm Device
        SensorNodeConnectionResponse connection =
                farmDeviceClient.getNodeConnection(nodeId);

        // 2. Kiểm tra trạng thái Node
        if (!"ACTIVE".equalsIgnoreCase(connection.status())) {
            throw new IllegalStateException(
                    "Sensor Node is not ACTIVE: " + nodeId
            );
        }

        // 3. Kiểm tra giao thức
        if (!"HTTP".equalsIgnoreCase(connection.connectionType())) {
            throw new IllegalStateException(
                    "Unsupported connection type: "
                            + connection.connectionType()
            );
        }

        // 4. Lấy endpoint động
        String endpointUrl = connection.endpointUrl();

        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalStateException(
                    "Endpoint not configured for node: " + nodeId
            );
        }

        URI baseUri = URI.create(endpointUrl);

        if (!"http".equalsIgnoreCase(baseUri.getScheme())
                || baseUri.getHost() == null
                || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null
                || baseUri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Invalid ESP32 endpoint"
            );
        }

        // TODO: Kiểm tra host/IP thuộc mạng thiết bị được phép
        // trước khi gửi request trong môi trường production.

        URI readingsUri = URI.create(
                endpointUrl.replaceAll("/+$", "") + "/readings"
        );

        // 5. Gọi ESP32 thực tế
        return restClient.get()
                .uri(readingsUri)
                .retrieve()
                .body(JsonNode.class);
    }
}
