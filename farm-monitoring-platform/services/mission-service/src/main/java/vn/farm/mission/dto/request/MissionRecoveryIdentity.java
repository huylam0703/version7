package vn.farm.mission.dto.request;

import java.util.UUID;

public record MissionRecoveryIdentity(
        UUID instanceId,
        Long recoveryGeneration
) {
}