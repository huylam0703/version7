
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionRecoveryClaimResponse;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionRecoveryLeaseManager {

    private final MissionClient missionClient;
    private final RecoveryLeaseRegistry leaseRegistry;

    public void register(MissionRecoveryClaimResponse claim) {

        if (claim == null
                || claim.missionId() == null
                || claim.executionId() == null
                || claim.instanceId() == null
                || claim.leaseUntil() == null
                || !Instant.now().plusSeconds(5)
                .isBefore(claim.leaseUntil())) {

            throw new IllegalArgumentException(
                    "Invalid or expired recovery claim"
            );
        }

        leaseRegistry.register(claim);
    }

    public MissionRecoveryClaimResponse getLease(UUID executionId) {
        return leaseRegistry.getForWrite(executionId);
    }

    public void renew(UUID executionId) {

        MissionRecoveryClaimResponse lease =
                leaseRegistry.getForWrite(executionId);

        if (lease == null) {
            return;
        }

        MissionRecoveryClaimResponse renewed;

        try {
            renewed = missionClient.renewRecoveryLease(
                    lease.missionId(),
                    lease.executionId(),
                    lease.instanceId(),
                    lease.recoveryGeneration()
            );

        } catch (RestClientResponseException e) {

            int status = e.getStatusCode().value();

            if (status == 403 || status == 409) {

                leaseRegistry.markLost(executionId);

                throw new RecoveryOwnershipLostException(
                        "Recovery ownership rejected during renewal: "
                                + executionId,
                        e
                );
            }

            // Network/server errors are handled by the scheduler.
            throw e;
        }

        if (renewed == null
                || !lease.missionId().equals(renewed.missionId())
                || !lease.executionId().equals(renewed.executionId())
                || !lease.instanceId().equals(renewed.instanceId())
                || lease.recoveryGeneration()
                != renewed.recoveryGeneration()
                || renewed.leaseUntil() == null
                || !Instant.now().plusSeconds(5)
                .isBefore(renewed.leaseUntil())) {

            leaseRegistry.markLost(executionId);

            throw new RecoveryOwnershipLostException(
                    "Invalid recovery lease renewal response: "
                            + executionId
            );
        }

        boolean updated =
                leaseRegistry.registerIfCurrent(lease, renewed);

        if (!updated) {
            throw new IllegalStateException(
                    "Recovery lease changed during renewal: "
                            + executionId
            );
        }
    }

    public boolean hasValidLease(UUID executionId) {

        try {
            return leaseRegistry.getForWrite(executionId) != null;

        } catch (IllegalStateException e) {
            return false;
        }
    }

    public void requireValidLease(UUID executionId) {

        if (!hasValidLease(executionId)) {
            throw new RecoveryOwnershipLostException(
                    "Recovery lease missing, expired or lost: "
                            + executionId
            );
        }
    }

    public MissionRecoveryClaimResponse getValidLeaseOrNull(
            UUID executionId
    ) {
        return leaseRegistry.getForWrite(executionId);
    }

    public Set<UUID> getActiveExecutionIds() {
        return leaseRegistry.activeExecutionIds();
    }

    public void remove(UUID executionId) {
        leaseRegistry.removeAfterCompletion(executionId);
    }

    public void markLost(UUID executionId) {
        leaseRegistry.markLost(executionId);
    }
}
