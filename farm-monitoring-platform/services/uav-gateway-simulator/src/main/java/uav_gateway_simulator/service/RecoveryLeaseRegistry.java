
package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;
import uav_gateway_simulator.dto.MissionRecoveryClaimResponse;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RecoveryLeaseRegistry {

    private final ConcurrentMap<UUID, MissionRecoveryClaimResponse> leases =
            new ConcurrentHashMap<>();

    private final Set<UUID> recoveryExecutions =
            ConcurrentHashMap.newKeySet();

    private final Set<UUID> lostLeases =
            ConcurrentHashMap.newKeySet();

    // Called before starting recovery claim.
    public void beginRecovery(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        recoveryExecutions.add(executionId);
    }

    // Called only after successful recovery claim.
    public void register(MissionRecoveryClaimResponse lease) {

        if (lease == null
                || lease.executionId() == null
                || lease.instanceId() == null
                || lease.leaseUntil() == null) {
            throw new IllegalArgumentException(
                    "Invalid recovery lease"
            );
        }

        UUID executionId = lease.executionId();

        if (lostLeases.contains(executionId)) {
            throw new IllegalStateException(
                    "Recovery ownership already lost: " + executionId
            );
        }

        recoveryExecutions.add(executionId);
        leases.put(executionId, lease);
    }

    public MissionRecoveryClaimResponse getForWrite(UUID executionId) {

        if (lostLeases.contains(executionId)) {
            throw new IllegalStateException(
                    "Recovery ownership lost: " + executionId
            );
        }

        MissionRecoveryClaimResponse lease = leases.get(executionId);

        if (lease == null) {

            if (recoveryExecutions.contains(executionId)) {
                throw new IllegalStateException(
                        "Recovery claim required: " + executionId
                );
            }

            // Normal mission, not a recovery execution.
            return null;
        }

        if (lease.leaseUntil() == null
                || !Instant.now().plusSeconds(5)
                .isBefore(lease.leaseUntil())) {

            throw new IllegalStateException(
                    "Recovery lease expired: " + executionId
            );
        }

        return lease;
    }

    public Set<UUID> activeExecutionIds() {
        return Set.copyOf(leases.keySet());
    }

    public void markLost(UUID executionId) {
        if (executionId == null) {
            return;
        }

        recoveryExecutions.add(executionId);
        lostLeases.add(executionId);
        leases.remove(executionId);
    }

    // Call only after confirmed completion.
    public void removeAfterCompletion(UUID executionId) {
        leases.remove(executionId);
        recoveryExecutions.remove(executionId);
        lostLeases.remove(executionId);
    }

    public boolean registerIfCurrent(
            MissionRecoveryClaimResponse expected,
            MissionRecoveryClaimResponse renewed
    ) {

        if (expected == null || renewed == null) {
            throw new IllegalArgumentException(
                    "Recovery leases must not be null"
            );
        }

        UUID executionId = expected.executionId();

        if (!executionId.equals(renewed.executionId())
                || !expected.instanceId().equals(renewed.instanceId())
                || expected.recoveryGeneration()
                != renewed.recoveryGeneration()) {

            throw new IllegalArgumentException(
                    "Recovery ownership mismatch"
            );
        }

        if (lostLeases.contains(executionId)) {
            return false;
        }

        return leases.replace(
                executionId,
                expected,
                renewed
        );
    }

    public void cancelBeforeClaim(UUID executionId) {
        if (executionId == null) {
            return;
        }

        if (!leases.containsKey(executionId)
                && !lostLeases.contains(executionId)) {
            recoveryExecutions.remove(executionId);
        }
    }
}
