
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionProcessingRecoveryTest {

    private MissionExecutionService missionExecutionService;
    private MissionExecutionTracker executionTracker;
    private MissionResultSimulationService resultService;
    private MissionResultPayloadStore payloadStore;
    private ExecutorService executor;
    private MissionResultsRetryScheduler retryScheduler;
    private MissionClient missionClient;
    private MissionRecoveryLeaseManager recoveryLeaseManager;
    private SimulatorInstanceIdentity instanceIdentity;
    private RecoveryLeaseRegistry leaseRegistry;

    private MissionProcessingService processingService;

    private UUID missionId;
    private UUID executionId;
    private UUID instanceId;

    private MissionReadyPayload payload;

    @BeforeEach
    void setUp() {
        missionExecutionService = mock(MissionExecutionService.class);
        executionTracker = new MissionExecutionTracker();
        resultService = mock(MissionResultSimulationService.class);
        payloadStore = mock(MissionResultPayloadStore.class);
        executor = mock(ExecutorService.class);
        retryScheduler = mock(MissionResultsRetryScheduler.class);
        missionClient = mock(MissionClient.class);

        leaseRegistry = new RecoveryLeaseRegistry();

        recoveryLeaseManager = new MissionRecoveryLeaseManager(
                missionClient,
                leaseRegistry
        );

        instanceIdentity = new SimulatorInstanceIdentity();

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        instanceId = instanceIdentity.getInstanceId();

        payload = new MissionReadyPayload();
        payload.setMissionId(missionId);
        payload.setExecutionId(executionId);
        payload.setFarmId(UUID.randomUUID());
        payload.setUavId(UUID.randomUUID());
        payload.setGatewayId(UUID.randomUUID());

        // Run submitted tasks synchronously.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        processingService = new MissionProcessingService(
                missionExecutionService,
                executionTracker,
                resultService,
                payloadStore,
                executor,
                retryScheduler,
                missionClient,
                recoveryLeaseManager,
                instanceIdentity,
                leaseRegistry
        );
    }

    private MissionRecoveryClaimResponse validClaim() {
        return new MissionRecoveryClaimResponse(
                missionId,
                executionId,
                instanceId,
                1L,
                Instant.now().plusSeconds(120)
        );
    }

    private void mockValidClaim() {
        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instanceId
        )).thenReturn(validClaim());
    }

    @Test
    void shouldClaimOwnershipBeforeResumingMission() {
        mockValidClaim();

        boolean accepted = processingService.recover(payload);

        assertTrue(accepted);

        InOrder order = inOrder(
                missionClient,
                missionExecutionService
        );

        order.verify(missionClient).claimRecovery(
                missionId,
                executionId,
                instanceId
        );

        order.verify(missionExecutionService)
                .resumeMission(payload);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                executionTracker.getStatus(executionId)
        );

        assertTrue(leaseRegistry.activeExecutionIds().isEmpty());

        verify(payloadStore).remove(executionId);
    }

    @Test
    void shouldNotResumeWhenClaimFails() {
        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instanceId
        )).thenThrow(
                new IllegalStateException("Claim rejected")
        );

        boolean accepted = processingService.recover(payload);

        // Accepted for executor scheduling, not successful recovery.
        assertTrue(accepted);

        verify(missionExecutionService, never())
                .resumeMission(any());

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                executionTracker.getStatus(executionId)
        );
    }

    @Test
    void shouldRejectInvalidClaimResponse() {
        MissionRecoveryClaimResponse invalidClaim =
                new MissionRecoveryClaimResponse(
                        UUID.randomUUID(),
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                );

        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instanceId
        )).thenReturn(invalidClaim);

        assertTrue(processingService.recover(payload));

        verify(missionExecutionService, never())
                .resumeMission(any());

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                executionTracker.getStatus(executionId)
        );
    }

    @Test
    void shouldRollbackWhenExecutorRejectsTask() {
        doThrow(new RejectedExecutionException("Executor stopped"))
                .when(executor)
                .execute(any(Runnable.class));

        assertThrows(
                RejectedExecutionException.class,
                () -> processingService.recover(payload)
        );

        assertNull(executionTracker.getStatus(executionId));

        verifyNoInteractions(missionClient);
        verifyNoInteractions(missionExecutionService);
    }

    @Test
    void shouldIgnoreDuplicateRecovery() {
        assertTrue(executionTracker.tryStartRecovery(executionId));

        boolean accepted = processingService.recover(payload);

        assertFalse(accepted);

        verifyNoInteractions(missionClient);
        verifyNoInteractions(missionExecutionService);
    }

    @Test
    void shouldNotResumeWhenClaimExpired() {
        MissionRecoveryClaimResponse expiredClaim =
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().minusSeconds(10)
                );

        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instanceId
        )).thenReturn(expiredClaim);

        assertTrue(processingService.recover(payload));

        verify(missionExecutionService, never())
                .resumeMission(any());

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                executionTracker.getStatus(executionId)
        );
    }

    @Test
    void shouldMarkFailedWhenOwnershipLostDuringResume() {
        mockValidClaim();

        doAnswer(invocation -> {
            leaseRegistry.markLost(executionId);
            throw new IllegalStateException("Ownership lost");
        }).when(missionExecutionService).resumeMission(payload);

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                executionTracker.getStatus(executionId)
        );

        assertFalse(
                recoveryLeaseManager.hasValidLease(executionId)
        );

        verify(payloadStore, never()).remove(executionId);
    }
}
