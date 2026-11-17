
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

        log.info(
                "Sending Mission Results: missionId={}, executionId={}",
                missionId,
                pending.request().executionId()
        );

        MissionResultResponse response = missionClient.sendResults(
                missionId,
                pending.request()
        );

        if (response == null
                || !missionId.equals(response.missionId())
                || !pending.request().executionId()
                .equals(response.executionId())
                || !"COMPLETED".equals(response.status())) {
            throw new IllegalStateException(
                    "Mission Results response is invalid"
            );
        }

        log.info(
                "Mission Results confirmed: missionId={}, executionId={}",
                missionId,
                pending.request().executionId()
        );

        // Chưa xóa payload tại đây.
        // Payload sẽ được xóa sau khi tracker
        // xác nhận COMPLETED.
        return response;
    }
}
