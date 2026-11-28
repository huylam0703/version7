
package uav_gateway_simulator.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import uav_gateway_simulator.dto.response.ApiResponse;
import uav_gateway_simulator.dto.response.MissionDetailResponse;

import java.time.Duration;
import java.util.UUID;

@Component
public class TelemetryMissionClient {

    private final WebClient webClient;

    public TelemetryMissionClient(
            @Value("${integration.mission.base-url}")
            String baseUrl
    ) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Mono<MissionDetailResponse> getMissionDetail(
            UUID missionId,
            String accessToken
    ) {
        return webClient.get()
                .uri("/{missionId}", missionId)
                .headers(headers ->
                        headers.setBearerAuth(accessToken)
                )
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        ApiResponse<MissionDetailResponse>>() {})
                .flatMap(response ->
                        Mono.justOrEmpty(response.result())
                )
                .timeout(Duration.ofSeconds(5));
    }
}
