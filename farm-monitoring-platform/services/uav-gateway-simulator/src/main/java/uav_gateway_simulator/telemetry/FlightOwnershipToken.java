
package uav_gateway_simulator.telemetry;

import java.util.UUID;

public record FlightOwnershipToken(
        UUID executionId,
        UUID instanceId,
        Long recoveryGeneration
) {
    public boolean isRecovery() {
        return recoveryGeneration != null;
    }
}
