package uav_gateway_simulator.dto;

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