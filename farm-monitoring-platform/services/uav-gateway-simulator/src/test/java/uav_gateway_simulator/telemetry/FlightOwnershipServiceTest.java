
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FlightOwnershipServiceTest {

    private RecoveryLeaseRegistry leaseRegistry;
    private FlightOwnershipService ownershipService;

    private UUID missionId;
    private UUID executionId;
    private UUID instanceId;

    @BeforeEach
    void setUp() {
        leaseRegistry = new RecoveryLeaseRegistry();

        ownershipService =
                new FlightOwnershipService(leaseRegistry);

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        instanceId = UUID.randomUUID();
    }

    private MissionRecoveryClaimResponse lease(
            UUID owner,
            long generation,
            long durationSeconds
    ) {
        return new MissionRecoveryClaimResponse(
                missionId,
                executionId,
                owner,
                generation,
                Instant.now().plusSeconds(durationSeconds)
        );
    }

    @Test
    void shouldCaptureNormalExecution() {

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        assertEquals(executionId, token.executionId());
        assertNull(token.instanceId());
        assertNull(token.recoveryGeneration());
        assertFalse(token.isRecovery());

        assertDoesNotThrow(
                () -> ownershipService.validate(token)
        );
    }

    @Test
    void shouldCaptureRecoveryOwnership() {

        leaseRegistry.beginRecovery(executionId);

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        assertTrue(token.isRecovery());
        assertEquals(instanceId, token.instanceId());
        assertEquals(1L, token.recoveryGeneration());

        assertDoesNotThrow(
                () -> ownershipService.validate(token)
        );
    }

    @Test
    void shouldRejectPreviousGeneration() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken oldToken =
                ownershipService.capture(executionId);

        leaseRegistry.register(
                lease(instanceId, 2L, 120)
        );

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.validate(oldToken)
        );

        FlightOwnershipToken newToken =
                ownershipService.capture(executionId);

        assertDoesNotThrow(
                () -> ownershipService.validate(newToken)
        );
    }

    @Test
    void shouldRejectPreviousInstance() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken oldToken =
                ownershipService.capture(executionId);

        leaseRegistry.register(
                lease(UUID.randomUUID(), 1L, 120)
        );

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.validate(oldToken)
        );
    }

    @Test
    void shouldAllowLeaseRenewal() {

        MissionRecoveryClaimResponse original =
                lease(instanceId, 1L, 120);

        leaseRegistry.register(original);

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        MissionRecoveryClaimResponse renewed =
                lease(instanceId, 1L, 240);

        assertTrue(
                leaseRegistry.registerIfCurrent(
                        original,
                        renewed
                )
        );

        assertDoesNotThrow(
                () -> ownershipService.validate(token)
        );
    }

    @Test
    void shouldRejectLostOwnership() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        leaseRegistry.markLost(executionId);

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.validate(token)
        );
    }

    @Test
    void shouldRejectExpiredLease() {

        leaseRegistry.register(
                lease(instanceId, 1L, -10)
        );

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.capture(executionId)
        );
    }

    @Test
    void shouldRejectNormalWorkerAfterRecoveryClaim() {

        FlightOwnershipToken normalToken =
                ownershipService.capture(executionId);

        leaseRegistry.beginRecovery(executionId);

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.validate(normalToken)
        );
    }

    @Test
    void shouldNotMarkNewGenerationLostUsingOldToken() {
        RecoveryLeaseRegistry registry =
                new RecoveryLeaseRegistry();

        UUID missionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        registry.beginRecovery(executionId);

        registry.register(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(60)
                )
        );

        FlightOwnershipToken oldToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        1L
                );

        registry.register(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        2L,
                        Instant.now().plusSeconds(60)
                )
        );

        assertFalse(registry.markLostIfCurrent(oldToken));

        assertEquals(
                2L,
                registry.getForWrite(executionId)
                        .recoveryGeneration()
        );
    }

    @Test
    void shouldNotRemoveNewGenerationUsingOldToken() {
        RecoveryLeaseRegistry registry =
                new RecoveryLeaseRegistry();

        UUID missionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();

        registry.beginRecovery(executionId);

        registry.register(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        2L,
                        Instant.now().plusSeconds(60)
                )
        );

        FlightOwnershipToken oldToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        1L
                );

        assertFalse(
                registry.removeAfterCompletionIfCurrent(oldToken)
        );

        assertNotNull(registry.getForWrite(executionId));

        FlightOwnershipToken currentToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        2L
                );

        assertTrue(
                registry.removeAfterCompletionIfCurrent(
                        currentToken
                )
        );

        assertNull(registry.getForWrite(executionId));
    }

    @Test
    void shouldExecuteActionWithCurrentOwnership() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        AtomicBoolean executed =
                new AtomicBoolean(false);

        ownershipService.executeIfCurrent(
                token,
                () -> executed.set(true)
        );

        assertTrue(executed.get());
    }

    @Test
    void shouldRejectActionFromPreviousGeneration() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken oldToken =
                ownershipService.capture(executionId);

        leaseRegistry.register(
                lease(instanceId, 2L, 120)
        );

        AtomicBoolean executed =
                new AtomicBoolean(false);

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.executeIfCurrent(
                        oldToken,
                        () -> executed.set(true)
                )
        );

        assertFalse(executed.get());
    }

    @Test
    void shouldRejectActionWithExpiredLease() {

        leaseRegistry.register(
                lease(instanceId, 1L, -10)
        );

        FlightOwnershipToken token =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        1L
                );

        AtomicBoolean executed =
                new AtomicBoolean(false);

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.executeIfCurrent(
                        token,
                        () -> executed.set(true)
                )
        );

        assertFalse(executed.get());
    }

    @Test
    void shouldRejectActionAfterOwnershipLoss() {

        leaseRegistry.register(
                lease(instanceId, 1L, 120)
        );

        FlightOwnershipToken token =
                ownershipService.capture(executionId);

        leaseRegistry.markLost(executionId);

        AtomicBoolean executed =
                new AtomicBoolean(false);

        assertThrows(
                IllegalStateException.class,
                () -> ownershipService.executeIfCurrent(
                        token,
                        () -> executed.set(true)
                )
        );

        assertFalse(executed.get());
    }
}
