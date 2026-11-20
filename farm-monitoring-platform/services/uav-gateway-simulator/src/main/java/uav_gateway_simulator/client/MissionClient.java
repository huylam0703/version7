package uav_gateway_simulator.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uav_gateway_simulator.dto.*;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;
import uav_gateway_simulator.service.TokenService;
import uav_gateway_simulator.dto.MissionHeartbeatRequest;
import uav_gateway_simulator.dto.MissionHeartbeatResponse;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionResultResponse;
import uav_gateway_simulator.dto.MissionProgressDetailResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.util.List;
import java.util.UUID;

@Component
public class MissionClient {

    private final RestClient restClient;
    private final RecoveryLeaseRegistry leaseRegistry;


    public MissionClient(
            RestClient.Builder restClientBuilder,
            TokenService tokenService,
            RecoveryLeaseRegistry leaseRegistry,
            @Value("${integration.mission.base-url}") String baseUrl
    ) {
        this.leaseRegistry = leaseRegistry;

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {

                    String token = tokenService.getAccessToken();

                    request.getHeaders().setBearerAuth(token);

                    ClientHttpResponse response =
                            execution.execute(request, body);

                    if (response.getStatusCode().value() == 401) {

                        response.close();

                        tokenService.invalidateToken();

                        String newToken = tokenService.getAccessToken();

                        request.getHeaders().setBearerAuth(newToken);

                        return execution.execute(request, body);
                    }

                    return response;
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
        MissionRecoveryClaimResponse lease =
                leaseRegistry.getForWrite(request.executionId());

        MissionProgressRequest requestWithOwnership =
                new MissionProgressRequest(
                        request.executionId(),
                        request.sequence(),
                        request.waypointSeq(),
                        request.reachedAt(),
                        request.nodeResults(),
                        lease == null ? null : lease.instanceId(),
                        lease == null ? null : lease.recoveryGeneration()
                );

        ApiResponse<MissionProgressResponse> response;

        try {
            response = restClient.post()
                    .uri("/{missionId}/progress", missionId)
                    .body(requestWithOwnership)
                    .retrieve()
                    .body(new ParameterizedTypeReference<
                            ApiResponse<MissionProgressResponse>>() {});
        } catch (RestClientResponseException e) {
            handleRecoveryWriteFailure(
                    request.executionId(),
                    e
            );

            throw e;
        }

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

        MissionRecoveryClaimResponse lease =
                leaseRegistry.getForWrite(executionId);

        MissionHeartbeatRequest request =
                new MissionHeartbeatRequest(
                        executionId,
                        lease == null ? null : lease.instanceId(),
                        lease == null ? null : lease.recoveryGeneration()
                );

        ApiResponse<MissionHeartbeatResponse> response;

        try {
            response = restClient.post()
                    .uri("/{missionId}/heartbeat", missionId)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<
                            ApiResponse<MissionHeartbeatResponse>>() {});
        } catch (RestClientResponseException e) {
            handleRecoveryWriteFailure(
                    request.executionId(),
                    e
            );

            throw e;
        }

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
        MissionRecoveryClaimResponse lease =
                leaseRegistry.getForWrite(request.executionId());

        MissionResultRequest requestWithOwnership =
                new MissionResultRequest(
                        request.executionId(),
                        request.results(),
                        request.completed(),
                        lease == null ? null : lease.instanceId(),
                        lease == null ? null : lease.recoveryGeneration()
                );

        ApiResponse<MissionResultResponse> response;

        try {
            response = restClient.post()
                    .uri("/{missionId}/results", missionId)
                    .body(requestWithOwnership)
                    .retrieve()
                    .body(new ParameterizedTypeReference<
                            ApiResponse<MissionResultResponse>>() {});
        } catch (RestClientResponseException e) {
            handleRecoveryWriteFailure(
                    request.executionId(),
                    e
            );

            throw e;
        }

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

    public List<RecoverableMissionResponse> getRecoverableMissions() {

        ApiResponse<List<RecoverableMissionResponse>> response =
                restClient.get()
                        .uri("/recoverable")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Recoverable missions response is empty"
            );
        }

        return response.result();
    }

    public MissionRecoveryClaimResponse claimRecovery(
            UUID missionId,
            UUID executionId,
            UUID instanceId
    ) {
        ApiResponse<MissionRecoveryClaimResponse> response =
                restClient.post()
                        .uri("/{missionId}/recovery-claim", missionId)
                        .body(new MissionRecoveryClaimRequest(
                                executionId,
                                instanceId
                        ))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Recovery claim response is empty"
            );
        }

        return response.result();
    }

    public MissionRecoveryClaimResponse renewRecoveryLease(
            UUID missionId,
            UUID executionId,
            UUID instanceId,
            long generation
    ) {
        ApiResponse<MissionRecoveryClaimResponse> response =
                restClient.post()
                        .uri("/{missionId}/recovery-renew", missionId)
                        .body(new MissionRecoveryRenewRequest(
                                executionId,
                                instanceId,
                                generation
                        ))
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.result() == null) {
            throw new IllegalStateException(
                    "Recovery renew response is empty"
            );
        }

        return response.result();
    }

    private void handleRecoveryWriteFailure(
            UUID executionId,
            RestClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();

        // Chỉ xử lý các execution có Recovery Lease.
        MissionRecoveryClaimResponse lease;

        try {
            lease = leaseRegistry.getForWrite(executionId);
        } catch (IllegalStateException e) {
            throw new RecoveryOwnershipLostException(
                    "Recovery lease is no longer valid: " + executionId,
                    e
            );
        }

        if (lease != null && (status == 403 || status == 409)) {

            leaseRegistry.markLost(executionId);

            throw new RecoveryOwnershipLostException(
                    "Recovery write rejected: executionId="
                            + executionId + ", HTTP " + status,
                    exception
            );
        }

        throw exception;
    }

}