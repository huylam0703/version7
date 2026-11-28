
package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.Objects;
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
    private final ConcurrentMap<UUID, FlightOwnershipToken> lostOwnershipTokens =
            new ConcurrentHashMap<>();

    // Called before starting recovery claim.
    public synchronized  void beginRecovery(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        recoveryExecutions.add(executionId);
    }

    // Called only after successful recovery claim.
    public synchronized  void register(MissionRecoveryClaimResponse lease) {

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

    public synchronized  MissionRecoveryClaimResponse getForWrite(UUID executionId) {

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

    public synchronized  Set<UUID> activeExecutionIds() {
        return Set.copyOf(leases.keySet());
    }

    public synchronized void markLost(UUID executionId) {

        if (executionId == null) {
            return;
        }

        MissionRecoveryClaimResponse current =
                leases.get(executionId);

        if (current != null) {

            lostOwnershipTokens.put(
                    executionId,
                    new FlightOwnershipToken(
                            executionId,
                            current.instanceId(),
                            current.recoveryGeneration()
                    )
            );
        }

        recoveryExecutions.add(executionId);
        lostLeases.add(executionId);
        leases.remove(executionId);
    }

    // Call only after confirmed completion.
    public synchronized void removeAfterCompletion(
            UUID executionId
    ) {
        leases.remove(executionId);
        recoveryExecutions.remove(executionId);
        lostLeases.remove(executionId);
        lostOwnershipTokens.remove(executionId);
    }

    public synchronized  boolean registerIfCurrent(
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

    public synchronized void cancelBeforeClaim(UUID executionId) {
        if (executionId == null) {
            return;
        }

        if (!leases.containsKey(executionId)
                && !lostLeases.contains(executionId)) {
            recoveryExecutions.remove(executionId);
        }
    }

    private boolean matchesOwnership(
            FlightOwnershipToken expected
    ) {
        if (expected == null
                || expected.executionId() == null) {
            return false;
        }

        UUID executionId = expected.executionId();

        if (lostLeases.contains(executionId)) {
            return false;
        }

        MissionRecoveryClaimResponse current =
                leases.get(executionId);

        if (current == null) {
            return !recoveryExecutions.contains(executionId)
                    && expected.instanceId() == null
                    && expected.recoveryGeneration() == null;
        }

        return Objects.equals(
                current.instanceId(),
                expected.instanceId()
        ) && Objects.equals(
                current.recoveryGeneration(),
                expected.recoveryGeneration()
        );
    }

    public synchronized boolean markLostIfCurrent(
            FlightOwnershipToken expected
    ) {
        if (!matchesOwnership(expected)) {
            return false;
        }

        markLost(expected.executionId());

        return true;
    }

    public synchronized boolean removeAfterCompletionIfCurrent(
            FlightOwnershipToken expected
    ) {
        if (!matchesOwnership(expected)) {
            return false;
        }

        removeAfterCompletion(expected.executionId());

        return true;
    }

    public synchronized boolean runIfCurrent(
            FlightOwnershipToken ownershipToken,
            Runnable action
    ) {
        if (!matchesOwnership(ownershipToken)) {
            return false;
        }

        action.run();

        return true;
    }

    public synchronized boolean runBeforeClaim(
            UUID executionId,
            Runnable action
    ) {
        if (executionId == null
                || !recoveryExecutions.contains(executionId)
                || leases.containsKey(executionId)
                || lostLeases.contains(executionId)) {
            return false;
        }

        action.run();

        return true;
    }
    public synchronized boolean runIfLostOwner(
            FlightOwnershipToken ownershipToken,
            Runnable action
    ) {

        if (ownershipToken == null
                || ownershipToken.executionId() == null) {
            return false;
        }

        UUID executionId = ownershipToken.executionId();

        if (!lostLeases.contains(executionId)
                || leases.containsKey(executionId)
                || !ownershipToken.equals(
                lostOwnershipTokens.get(executionId)
        )) {
            return false;
        }

        action.run();

        return true;
    }
}
