
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionProgressDetailResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.dto.request.MissionNodeResultRequest;
import uav_gateway_simulator.dto.request.MissionResultRequest;

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

    public MissionResultResponse simulateResults(
            UUID missionId,
            UUID executionId,
            List<UUID> nodeIds
    ) {
        if (missionId == null || executionId == null) {
            throw new IllegalArgumentException(
                    "MissionId and ExecutionId must not be null"
            );
        }
        // Không cho phép gửi Results nếu Recovery đã mất quyền.
        leaseRegistry.getForWrite(executionId);

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
                payloadStore.saveIfAbsent(missionId, request);

        return sendPendingResults(pending);
    }

    public MissionResultResponse retryResults(UUID executionId) {
        leaseRegistry.getForWrite(executionId);
        MissionResultPayloadStore.PendingResult pending =
                payloadStore.get(executionId);

        if (pending == null) {
            throw new IllegalStateException(
                    "No pending Mission Results for executionId="
                            + executionId
            );
        }

        return sendPendingResults(pending);
    }


    private MissionResultResponse sendPendingResults(
            MissionResultPayloadStore.PendingResult pending
    ) {
        UUID missionId = pending.missionId();
        UUID executionId = pending.request().executionId();

        // Kiểm tra lease trước mỗi lần gửi Results.
        // MissionClient sẽ gắn ownership vào HTTP request.
        leaseRegistry.getForWrite(executionId);

        cancellationService.checkNotCancelled(missionId);

        log.info(
                "Sending Mission Results: missionId={}, executionId={}",
                missionId,
                executionId
        );

        MissionResultResponse response = missionClient.sendResults(
                missionId,
                pending.request()
        );

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

        // Không xóa payload ở đây.
        // MissionProcessingService sẽ xóa sau khi tracker
        // xác nhận COMPLETED.
        return response;
    }

}
