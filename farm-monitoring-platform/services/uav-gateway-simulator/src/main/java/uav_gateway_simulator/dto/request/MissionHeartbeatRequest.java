package uav_gateway_simulator.dto.request;

import java.util.UUID;

public record MissionHeartbeatRequest(
        UUID executionId,
        UUID instanceId,
        Long recoveryGeneration
) {
    public MissionHeartbeatRequest(UUID executionId) {
        this(executionId, null, null);
    }
}