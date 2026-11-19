package vn.farm.mission.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MissionRecoveryRenewRequest(
        @NotNull UUID executionId,
        @NotNull UUID instanceId,
        long recoveryGeneration
) {
}