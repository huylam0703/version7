package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MissionResultPayloadStore {

    private final ConcurrentMap<UUID, PendingResult> pendingResults =
            new ConcurrentHashMap<>();

    public record PendingResult(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken
    ) {}

    public PendingResult saveIfAbsent(
            UUID missionId,
            MissionResultRequest request
    ) {
        validatePayload(missionId, request);

        return saveIfAbsent(
                missionId,
                request,
                new FlightOwnershipToken(
                        request.executionId(),
                        null,
                        null
                )
        );
    }

    public PendingResult saveIfAbsent(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken
    ) {
        validatePayload(missionId, request);

        if (ownershipToken == null
                || !request.executionId().equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Results ownership token mismatch"
            );
        }

        PendingResult pending = new PendingResult(
                missionId,
                request,
                ownershipToken
        );

        PendingResult existing = pendingResults.putIfAbsent(
                request.executionId(),
                pending
        );

        if (existing != null) {
            if (!existing.missionId().equals(missionId)) {
                throw new IllegalStateException(
                        "ExecutionId belongs to another mission"
                );
            }

            if (!existing.ownershipToken().equals(ownershipToken)) {
                throw new IllegalStateException(
                        "Pending Results ownership mismatch"
                );
            }

            return existing;
        }

        return pending;
    }

    private void validatePayload(
            UUID missionId,
            MissionResultRequest request
    ) {
        if (missionId == null
                || request == null
                || request.executionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid Mission Results payload"
            );
        }
    }

    public PendingResult get(UUID executionId) {
        return pendingResults.get(executionId);
    }

    public void remove(UUID executionId) {
        pendingResults.remove(executionId);
    }

    public boolean remove(PendingResult expected) {
        if (expected == null) {
            return false;
        }

        return pendingResults.remove(
                expected.request().executionId(),
                expected
        );
    }

    public PendingResult saveForCurrentOwner(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken,
            RecoveryLeaseRegistry leaseRegistry
    ) {
        validatePayload(missionId, request);

        if (ownershipToken == null
                || !request.executionId().equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Results ownership token mismatch"
            );
        }

        synchronized (leaseRegistry) {

            new uav_gateway_simulator.telemetry.FlightOwnershipService(
                    leaseRegistry
            ).validate(ownershipToken);

            return pendingResults.compute(
                    request.executionId(),
                    (id, existing) -> {

                        if (existing != null) {

                            if (!existing.missionId().equals(missionId)) {
                                throw new IllegalStateException(
                                        "ExecutionId belongs to another mission"
                                );
                            }

                            if (existing.ownershipToken()
                                    .equals(ownershipToken)) {
                                return existing;
                            }
                        }

                        return new PendingResult(
                                missionId,
                                request,
                                ownershipToken
                        );
                    }
            );
        }
    }
}