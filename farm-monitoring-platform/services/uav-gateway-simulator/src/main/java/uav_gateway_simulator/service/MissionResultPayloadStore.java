
package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;
import uav_gateway_simulator.dto.request.MissionResultRequest;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MissionResultPayloadStore {

    private final ConcurrentMap<UUID, PendingResult> pendingResults =
            new ConcurrentHashMap<>();

    public record PendingResult(
            UUID missionId,
            MissionResultRequest request
    ) {}

    public PendingResult saveIfAbsent(
            UUID missionId,
            MissionResultRequest request
    ) {
        if (missionId == null
                || request == null
                || request.executionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid Mission Results payload"
            );
        }

        PendingResult pending = new PendingResult(
                missionId,
                request
        );

        PendingResult existing = pendingResults.putIfAbsent(
                request.executionId(),
                pending
        );

        if (existing != null) {
            if (!existing.missionId().equals(missionId)) {
                throw new IllegalStateException(
                        "ExecutionId belongs to another mission"
                );
            }

            return existing;
        }

        return pending;
    }

    public PendingResult get(UUID executionId) {
        return pendingResults.get(executionId);
    }

    public void remove(UUID executionId) {
        pendingResults.remove(executionId);
    }
}
