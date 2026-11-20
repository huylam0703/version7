
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionResultSimulationService {

    private final MissionClient missionClient;
    private final MissionResultPayloadStore payloadStore;
    private final RecoveryLeaseRegistry leaseRegistry;

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

        Instant collectedAt = Instant.now();

        List<MissionNodeResultRequest> results = nodeIds.stream()
                .map(nodeId -> new MissionNodeResultRequest(
                        nodeId,
                        "SUCCESS",
                        10,
                        1,
                        collectedAt,
                        null,
                        null
                ))
                .toList();

        MissionResultRequest request = new MissionResultRequest(
                executionId,
                results,
                true
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
