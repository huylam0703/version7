package vn.farm.mission.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.farm.mission.dto.integration.MissionCheckRequest;
import vn.farm.mission.dto.response.MissionCheckResponse;

import java.time.Duration;

@Component
public class FarmDeviceClient {

    private final RestClient restClient;

    public FarmDeviceClient(
            @Value("${integration.farm-device.base-url}")
            String baseUrl
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public MissionCheckResponse checkMissionReferences(
            MissionCheckRequest request
    ) {
        return restClient.post()
                .uri("/internal/v1/references/mission-check")
                .body(request)
                .retrieve()
                .body(MissionCheckResponse.class);
    }
}
