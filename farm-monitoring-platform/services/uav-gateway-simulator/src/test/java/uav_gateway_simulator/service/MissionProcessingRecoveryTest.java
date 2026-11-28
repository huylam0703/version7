
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionProcessingRecoveryTest {

    private FlightLifecycleService flightLifecycleService;
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
        flightLifecycleService =
                mock(FlightLifecycleService.class);

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
                leaseRegistry,
                flightLifecycleService
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

        verify(payloadStore).get(executionId);

        verify(payloadStore, never())
                .remove(executionId);

        verify(payloadStore, never())
                .remove(any(MissionResultPayloadStore.PendingResult.class));
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

    private MissionRecoveryClaimResponse replaceRecoveryGeneration() {

        MissionRecoveryClaimResponse newClaim =
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        2L,
                        Instant.now().plusSeconds(120)
                );

        leaseRegistry.register(newClaim);

        return newClaim;
    }

    @Test
    void shouldIgnoreStaleRecoveryCompletion() {

        mockValidClaim();

        doAnswer(invocation -> {

            replaceRecoveryGeneration();

            return null;

        }).when(missionExecutionService)
                .resumeMission(payload);

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.PROCESSING,
                executionTracker.getStatus(executionId)
        );

        assertEquals(
                2L,
                leaseRegistry.getForWrite(executionId)
                        .recoveryGeneration()
        );

        verifyNoInteractions(payloadStore);
    }

    @Test
    void shouldIgnoreStaleRecoveryCancellation() {

        mockValidClaim();

        doAnswer(invocation -> {

            replaceRecoveryGeneration();

            throw new MissionCancelledException(
                    "Stale worker cancelled"
            );

        }).when(missionExecutionService)
                .resumeMission(payload);

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.PROCESSING,
                executionTracker.getStatus(executionId)
        );

        assertEquals(
                2L,
                leaseRegistry.getForWrite(executionId)
                        .recoveryGeneration()
        );

        verifyNoInteractions(payloadStore);
    }

    @Test
    void shouldIgnoreStaleRecoveryFailure() {

        mockValidClaim();

        doAnswer(invocation -> {

            replaceRecoveryGeneration();

            throw new IllegalStateException(
                    "Stale worker failed"
            );

        }).when(missionExecutionService)
                .resumeMission(payload);

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.PROCESSING,
                executionTracker.getStatus(executionId)
        );

        assertEquals(
                2L,
                leaseRegistry.getForWrite(executionId)
                        .recoveryGeneration()
        );

        verifyNoInteractions(payloadStore);
        verifyNoInteractions(retryScheduler);
    }

    @Test
    void shouldNotRemovePayloadOwnedByAnotherGeneration() {

        mockValidClaim();

        FlightOwnershipToken newToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        2L
                );

        MissionResultRequest request =
                new MissionResultRequest(
                        executionId,
                        List.of(),
                        true,
                        instanceId,
                        2L
                );

        MissionResultPayloadStore.PendingResult pending =
                new MissionResultPayloadStore.PendingResult(
                        missionId,
                        request,
                        newToken
                );

        when(payloadStore.get(executionId))
                .thenReturn(pending);

        assertTrue(processingService.recover(payload));

        verify(payloadStore).get(executionId);

        verify(payloadStore, never())
                .remove(executionId);

        verify(payloadStore, never())
                .remove(any(
                        MissionResultPayloadStore.PendingResult.class
                ));
    }

    @Test
    void shouldRemovePayloadOwnedByCurrentGeneration() {

        mockValidClaim();

        FlightOwnershipToken ownershipToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        1L
                );

        MissionResultRequest request =
                new MissionResultRequest(
                        executionId,
                        List.of(),
                        true,
                        instanceId,
                        1L
                );

        MissionResultPayloadStore.PendingResult pending =
                new MissionResultPayloadStore.PendingResult(
                        missionId,
                        request,
                        ownershipToken
                );

        when(payloadStore.get(executionId))
                .thenReturn(pending);

        when(payloadStore.remove(pending))
                .thenReturn(true);

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                executionTracker.getStatus(executionId)
        );

        verify(payloadStore).remove(pending);

        verify(payloadStore, never())
                .remove(executionId);

        assertTrue(
                leaseRegistry.activeExecutionIds().isEmpty()
        );
    }
    @Test
    void shouldCleanupRecoveryWhenCancellationTelemetryFails() {
        mockValidClaim();
        FlightOwnershipToken token = new FlightOwnershipToken(executionId, instanceId, 1L);
        MissionResultRequest request = new MissionResultRequest(executionId, List.of(), true, instanceId, 1L);
        var pending = new MissionResultPayloadStore.PendingResult(missionId, request, token);
        when(payloadStore.get(executionId)).thenReturn(pending);
        doThrow(new MissionCancelledException("Cancelled")).when(missionExecutionService).resumeMission(payload);
        doThrow(new IllegalStateException("Lease expired")).when(flightLifecycleService).onCancelled(missionId, executionId, token);
        assertTrue(processingService.recover(payload));
        assertEquals(MissionExecutionTracker.Status.CANCELLED, executionTracker.getStatus(executionId));
        verify(payloadStore).remove(pending);
        verify(flightLifecycleService).onCancelled(missionId, executionId, token);
        assertTrue(leaseRegistry.activeExecutionIds().isEmpty());
        verifyNoInteractions(retryScheduler);
    }
}
