package uav_gateway_simulator.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MissionProgressRequest(
        UUID executionId,
        long sequence,
        Short waypointSeq,
        Instant reachedAt,
        List<Object> nodeResults,
        UUID instanceId,
        Long recoveryGeneration
) {
    public MissionProgressRequest(
            UUID executionId,
            long sequence,
            Short waypointSeq,
            Instant reachedAt,
            List<Object> nodeResults
    ) {
        this(
                executionId,
                sequence,
                waypointSeq,
                reachedAt,
                nodeResults,
                null,
                null
        );
    }
}