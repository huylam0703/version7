package uav_gateway_simulator.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import uav_gateway_simulator.dto.*;
import uav_gateway_simulator.service.TokenService;
import uav_gateway_simulator.dto.MissionHeartbeatRequest;
import uav_gateway_simulator.dto.MissionHeartbeatResponse;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionResultResponse;
import uav_gateway_simulator.dto.MissionProgressDetailResponse;

import java.util.UUID;

@Component
public class MissionClient {

    private final RestClient restClient;

    public MissionClient(
            RestClient.Builder restClientBuilder,
            TokenService tokenService,
            @Value("${integration.mission.base-url}") String baseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {

                    String token = tokenService.getAccessToken();

                    request.getHeaders().setBearerAuth(token);

                    return execution.execute(request, body);
                })
                .build();
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public MissionDetailResponse getMissionDetail(UUID missionId) {

        ApiResponse<MissionDetailResponse> response = restClient.get()
                .uri("/{missionId}", missionId)
                .retrieve()
                .body(new ParameterizedTypeReference<
                                        ApiResponse<MissionDetailResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Mission Service returned empty mission detail"
            );
        }

        return response.result();
    }

    public MissionAckResponse ackMission(
            UUID missionId,
            UUID executionId
    ) {

        MissionAckRequest request =
                new MissionAckRequest(executionId);

        ApiResponse<MissionAckResponse> response =
                restClient.post()
                        .uri("/{missionId}/ack", missionId)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionAckResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Mission Service returned empty ACK response"
            );
        }

        return response.result();
    }

    public MissionProgressResponse updateProgress(
            UUID missionId,
            MissionProgressRequest request
    ) {
        ApiResponse<MissionProgressResponse> response =
                restClient.post()
                        .uri("/{missionId}/progress", missionId)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionProgressResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Empty mission progress response"
            );
        }

        return response.result();
    }

    public MissionHeartbeatResponse sendHeartbeat(
            UUID missionId,
            UUID executionId
    ) {

        MissionHeartbeatRequest request =
                new MissionHeartbeatRequest(executionId);

        ApiResponse<MissionHeartbeatResponse> response =
                restClient.post()
                        .uri("/{missionId}/heartbeat", missionId)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionHeartbeatResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Empty mission heartbeat response"
            );
        }

        MissionHeartbeatResponse result = response.result();

        if (!missionId.equals(result.missionId())
                || !executionId.equals(result.executionId())
                || !"EXECUTING".equals(result.executionStatus())
                || result.lastHeartbeatAt() == null) {

            throw new IllegalStateException(
                    "Invalid mission heartbeat confirmation"
            );
        }

        return result;
    }

    public MissionResultResponse sendResults(
            UUID missionId,
            MissionResultRequest request
    ) {
        ApiResponse<MissionResultResponse> response =
                restClient.post()
                        .uri("/{missionId}/results", missionId)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionResultResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Mission Results response is empty"
            );
        }

        MissionResultResponse result = response.result();

        if (!missionId.equals(result.missionId())) {
            throw new IllegalStateException(
                    "Mission Results response missionId mismatch"
            );
        }

        return result;
    }


    public MissionExecutionProgressResponse getExecutionProgress(
            UUID missionId,
            UUID executionId
    ) {
        ApiResponse<MissionExecutionProgressResponse> response =
                restClient.get()
                        .uri(
                                "/{missionId}/executions/{executionId}/progress",
                                missionId,
                                executionId
                        )
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionExecutionProgressResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Mission Service returned empty execution progress"
            );
        }

        MissionExecutionProgressResponse result = response.result();

        if (!missionId.equals(result.missionId())
                || !executionId.equals(result.executionId())
                || result.executionStatus() == null
                || result.executionStatus().isBlank()
                || result.lastConfirmedSequence() < 0) {

            throw new IllegalStateException(
                    "Invalid mission execution progress response"
            );
        }

        return result;
    }


    public MissionProgressDetailResponse getMissionProgress(
            UUID missionId
    ) {
        ApiResponse<MissionProgressDetailResponse> response =
                restClient.get()
                        .uri("/{missionId}/progress", missionId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<
                                ApiResponse<MissionProgressDetailResponse>>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Mission Service returned empty mission progress"
            );
        }

        MissionProgressDetailResponse result = response.result();

        if (!missionId.equals(result.missionId())
                || result.executionId() == null
                || result.waypoints() == null
                || result.lastConfirmedSequence() < 0) {
            throw new IllegalStateException(
                    "Invalid mission progress response"
            );
        }

        return result;
    }


}