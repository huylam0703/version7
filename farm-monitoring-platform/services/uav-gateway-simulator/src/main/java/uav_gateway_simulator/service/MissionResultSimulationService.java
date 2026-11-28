
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionProgressDetailResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.dto.request.MissionNodeResultRequest;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionResultSimulationService {

    private final MissionClient missionClient;
    private final MissionResultPayloadStore payloadStore;
    private final RecoveryLeaseRegistry leaseRegistry;
    private final MissionCancellationService cancellationService;
    private final FlightLifecycleService flightLifecycleService;
    private final FlightOwnershipService flightOwnershipService;

    public MissionResultResponse simulateResults(
            UUID missionId,
            UUID executionId,
            List<UUID> nodeIds
    ) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId is required"
            );
        }

        return simulateResults(
                missionId,
                executionId,
                nodeIds,
                flightOwnershipService.capture(executionId)
        );
    }

    public MissionResultResponse simulateResults(
            UUID missionId,
            UUID executionId,
            List<UUID> nodeIds,
            FlightOwnershipToken ownershipToken
    ) {
        if (missionId == null || executionId == null) {
            throw new IllegalArgumentException(
                    "MissionId and ExecutionId must not be null"
            );
        }
        // Không cho phép gửi Results nếu Recovery đã mất quyền.
        validateOwnership(executionId, ownershipToken);

        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mission must contain at least one node"
            );
        }

        if (nodeIds.stream().anyMatch(id -> id == null)
                || nodeIds.stream().distinct().count() != nodeIds.size()) {
            throw new IllegalArgumentException(
                    "Mission nodeIds must be non-null and unique"
            );
        }


        // Đọc Progress đã được Mission Service xác nhận.
        // Sử dụng đúng tên method hiện có trong MissionClient.
        MissionProgressDetailResponse progress =
                missionClient.getMissionProgress(missionId);
        flightOwnershipService.validate(ownershipToken);

        if (progress == null
                || !missionId.equals(progress.missionId())
                || !executionId.equals(progress.executionId())) {
            throw new IllegalStateException(
                    "Invalid Mission Progress Detail"
            );
        }

        // Chỉ tổng hợp Results khi toàn bộ Waypoint
        // và Node đã được xử lý.
        if (progress.reachedWaypoints() != progress.totalWaypoints()
                || progress.processedNodes() != progress.totalNodes()) {
            throw new IllegalStateException(
                    "Mission Progress is not fully confirmed"
            );
        }

        if (progress.nodes() == null) {
            throw new IllegalStateException(
                    "Mission Progress nodes must not be null"
            );
        }

        Map<UUID, MissionProgressDetailResponse.NodeProgress> nodeMap =
                progress.nodes().stream()
                        .collect(Collectors.toMap(
                                MissionProgressDetailResponse.NodeProgress::nodeId,
                                Function.identity()
                        ));

        if (nodeMap.size() != nodeIds.size()
                || !nodeMap.keySet().containsAll(nodeIds)) {
            throw new IllegalStateException(
                    "Mission Results nodes do not match Mission Progress"
            );
        }

        List<MissionNodeResultRequest> results = nodeIds.stream()
                .map(nodeId -> {
                    MissionProgressDetailResponse.NodeProgress node =
                            nodeMap.get(nodeId);

                    if (!"SUCCESS".equals(node.result())
                            && !"FAILED".equals(node.result())) {
                        throw new IllegalStateException(
                                "Invalid node result: " + nodeId
                        );
                    }

                    if ("SUCCESS".equals(node.result())
                            && node.recordsCount() <= 0) {
                        throw new IllegalStateException(
                                "SUCCESS node must have recordsCount > 0"
                        );
                    }

                    if ("FAILED".equals(node.result())
                            && node.recordsCount() != 0) {
                        throw new IllegalStateException(
                                "FAILED node must have recordsCount = 0"
                        );
                    }

                    return new MissionNodeResultRequest(
                            node.nodeId(),
                            node.result(),
                            node.recordsCount(),
                            node.attempts() == null ? 0 : node.attempts(),
                            node.collectedAt(),
                            null,
                            null
                    );
                })
                .toList();

        MissionResultRequest request = new MissionResultRequest(
                executionId,
                results,
                true
        );
        log.info(
                "FINAL RESULTS PAYLOAD: missionId={}, executionId={}, nodes={}",
                missionId,
                executionId,
                results
        );


        MissionResultPayloadStore.PendingResult pending =
                payloadStore.saveForCurrentOwner(
                        missionId,
                        request,
                        ownershipToken,
                        leaseRegistry
                );

        return sendPendingResults(pending, ownershipToken);
    }

    public MissionResultResponse retryResults(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId is required"
            );
        }

        MissionResultPayloadStore.PendingResult pending =
                payloadStore.get(executionId);

        if (pending == null) {
            throw new IllegalStateException(
                    "No pending Mission Results for executionId="
                            + executionId
            );
        }

        return retryResults(
                executionId,
                pending.ownershipToken()
        );
    }

    public MissionResultResponse retryResults(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnership(executionId, ownershipToken);

        MissionResultPayloadStore.PendingResult pending =
                payloadStore.get(executionId);

        if (pending == null) {
            throw new IllegalStateException(
                    "No pending Mission Results for executionId="
                            + executionId
            );
        }

        if (!pending.ownershipToken().equals(ownershipToken)) {
            throw new IllegalStateException(
                    "Pending Results ownership mismatch"
            );
        }

        return sendPendingResults(pending, ownershipToken);
    }



    private MissionResultResponse sendPendingResults(
            MissionResultPayloadStore.PendingResult pending,
            FlightOwnershipToken ownershipToken
    ) {
        UUID missionId = pending.missionId();
        UUID executionId = pending.request().executionId();

        validateOwnership(executionId, ownershipToken);
        if (!pending.ownershipToken().equals(ownershipToken)) {
            throw new IllegalStateException(
                    "Pending Results ownership mismatch"
            );
        }

        cancellationService.checkNotCancelled(missionId);

        flightOwnershipService.validate(ownershipToken);

        log.info(
                "Sending Mission Results: missionId={}, executionId={}",
                missionId,
                executionId
        );

        MissionResultResponse response =
                missionClient.sendResults(
                        missionId,
                        pending.request()
                );

        // Reject stale worker after receiving Results.
        flightOwnershipService.validate(ownershipToken);

        if (response == null
                || !missionId.equals(response.missionId())
                || !executionId.equals(response.executionId())
                || (!"COMPLETED".equals(response.status())
                && !"PARTIAL".equals(response.status())
                && !"FAILED".equals(response.status()))) {

            throw new IllegalStateException(
                    "Mission Results response is invalid"
            );
        }

        log.info(
                "Mission Results confirmed: missionId={}, executionId={}, status={}",
                missionId,
                executionId,
                response.status()
        );

        flightOwnershipService.validate(ownershipToken);

        // Update telemetry only after confirmed Results.
        flightLifecycleService.onMissionResult(
                missionId,
                executionId,
                response,
                ownershipToken
        );

        log.info(
                "[FLIGHT LIFECYCLE] Mission result confirmed: missionId={}, executionId={}, status={}",
                missionId,
                executionId,
                response.status()
        );

        // Payload cleanup remains in MissionProcessingService.
        return response;
    }

    private void validateOwnership(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        if (executionId == null
                || ownershipToken == null
                || !executionId.equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Results ownership token mismatch"
            );
        }

        flightOwnershipService.validate(ownershipToken);
    }
}
