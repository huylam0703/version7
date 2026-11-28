package uav_gateway_simulator.service;

import org.junit.jupiter.api.Test;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MissionResultPayloadStoreTest {
    private final UUID missionId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final RecoveryLeaseRegistry registry = new RecoveryLeaseRegistry();
    private final MissionResultPayloadStore store = new MissionResultPayloadStore();

    private FlightOwnershipToken register(long generation, Instant until) {
        registry.register(new MissionRecoveryClaimResponse(missionId, executionId, instanceId, generation, until));
        return new FlightOwnershipToken(executionId, instanceId, generation);
    }

    private MissionResultRequest request(long generation) {
        return new MissionResultRequest(executionId, List.of(), true, instanceId, generation);
    }

    @Test
    void shouldReplacePendingResultsForNewOwner() {
        var old = register(1L, Instant.now().plusSeconds(120));
        var previous = store.saveForCurrentOwner(missionId, request(1L), old, registry);
        var current = register(2L, Instant.now().plusSeconds(120));
        var nextRequest = request(2L);
        var next = store.saveForCurrentOwner(missionId, nextRequest, current, registry);
        assertEquals(current, next.ownershipToken());
        assertSame(nextRequest, next.request());
        assertFalse(store.remove(previous));
        assertSame(next, store.get(executionId));
    }

    @Test
    void shouldRejectStaleOwnerWithoutChangingPendingResults() {
        var old = register(1L, Instant.now().plusSeconds(120));
        var current = register(2L, Instant.now().plusSeconds(120));
        var next = store.saveForCurrentOwner(missionId, request(2L), current, registry);
        assertThrows(IllegalStateException.class, () -> store.saveForCurrentOwner(missionId, request(1L), old, registry));
        assertSame(next, store.get(executionId));
    }

    @Test
    void shouldRejectExpiredLeaseWithoutChangingPendingResults() {
        var old = register(1L, Instant.now().plusSeconds(120));
        var previous = store.saveForCurrentOwner(missionId, request(1L), old, registry);
        var expired = register(2L, Instant.now().minusSeconds(10));
        assertThrows(IllegalStateException.class, () -> store.saveForCurrentOwner(missionId, request(2L), expired, registry));
        assertSame(previous, store.get(executionId));
    }
}
