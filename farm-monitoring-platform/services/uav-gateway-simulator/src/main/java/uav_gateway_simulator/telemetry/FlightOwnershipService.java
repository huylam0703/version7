
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlightOwnershipService {

    private final RecoveryLeaseRegistry leaseRegistry;

    public FlightOwnershipToken capture(UUID executionId) {

        Objects.requireNonNull(
                executionId,
                "ExecutionId is required"
        );

        MissionRecoveryClaimResponse lease =
                leaseRegistry.getForWrite(executionId);

        if (lease == null) {
            return new FlightOwnershipToken(
                    executionId,
                    null,
                    null
            );
        }

        return new FlightOwnershipToken(
                executionId,
                lease.instanceId(),
                lease.recoveryGeneration()
        );
    }

    public void validate(FlightOwnershipToken expected) {

        Objects.requireNonNull(
                expected,
                "Flight ownership token is required"
        );

        FlightOwnershipToken current =
                capture(expected.executionId());

        if (!expected.equals(current)) {
            throw new IllegalStateException(
                    "Flight ownership changed: executionId="
                            + expected.executionId()
            );
        }
    }

    public boolean isCurrent(FlightOwnershipToken expected) {

        if (expected == null) {
            return false;
        }

        try {
            validate(expected);
            return true;

        } catch (IllegalStateException e) {
            return false;
        }
    }
    public void executeIfCurrent(
            FlightOwnershipToken ownershipToken,
            Runnable action
    ) {

        Objects.requireNonNull(
                ownershipToken,
                "Flight ownership token is required"
        );

        Objects.requireNonNull(
                action,
                "Ownership action is required"
        );

        synchronized (leaseRegistry) {

            validate(ownershipToken);

            action.run();
        }
    }
}
