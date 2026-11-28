
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.dto.response.RecoverableMissionResponse;
import uav_gateway_simulator.telemetry.FlightLifecycleService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionRestartRecoveryTest {

    private MissionClient missionClient;
    private MissionExecutionService executionService;
    private MissionResultSimulationService resultService;
    private MissionResultPayloadStore payloadStore;
    private MissionResultsRetryScheduler retryScheduler;
    private ExecutorService executor;

    private MissionExecutionTracker tracker;
    private RecoveryLeaseRegistry registry;
    private MissionRecoveryLeaseManager leaseManager;
    private SimulatorInstanceIdentity newInstance;
    private MissionRecoveryCoordinator coordinator;

    private UUID missionId;
    private UUID executionId;
    private UUID farmId;
    private UUID uavId;
    private UUID gatewayId;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);
        executionService = mock(MissionExecutionService.class);
        resultService = mock(MissionResultSimulationService.class);
        payloadStore = mock(MissionResultPayloadStore.class);
        retryScheduler = mock(MissionResultsRetryScheduler.class);
        executor = mock(ExecutorService.class);
        FlightLifecycleService flightLifecycleService =
                mock(FlightLifecycleService.class);
        // Simulate a previous Simulator instance.
        SimulatorInstanceIdentity oldInstance =
                new SimulatorInstanceIdentity();

        MissionExecutionTracker oldTracker =
                new MissionExecutionTracker();

        UUID previousExecutionId = UUID.randomUUID();
        assertTrue(oldTracker.tryStart(previousExecutionId));

        // Simulate restart: new in-memory components.
        newInstance = new SimulatorInstanceIdentity();
        tracker = new MissionExecutionTracker();
        registry = new RecoveryLeaseRegistry();

        assertNotEquals(
                oldInstance.getInstanceId(),
                newInstance.getInstanceId()
        );

        assertNull(tracker.getStatus(previousExecutionId));

        leaseManager = new MissionRecoveryLeaseManager(
                missionClient,
                registry
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        farmId = UUID.randomUUID();
        uavId = UUID.randomUUID();
        gatewayId = UUID.randomUUID();

        // Execute recovery workers synchronously.
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        MissionProcessingService processingService =
                new MissionProcessingService(
                        executionService,
                        tracker,
                        resultService,
                        payloadStore,
                        executor,
                        retryScheduler,
                        missionClient,
                        leaseManager,
                        newInstance,
                        registry,
                        flightLifecycleService
                );

        coordinator = new MissionRecoveryCoordinator(
                missionClient,
                processingService,
                tracker
        );
    }

    private RecoverableMissionResponse recoverableMission() {
        return new RecoverableMissionResponse(
                missionId,
                executionId,
                farmId,
                uavId,
                gatewayId
        );
    }

    private void mockSuccessfulRecovery() {
        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of(recoverableMission()));

        when(missionClient.claimRecovery(
                missionId,
                executionId,
                newInstance.getInstanceId()
        )).thenReturn(new MissionRecoveryClaimResponse(
                missionId,
                executionId,
                newInstance.getInstanceId(),
                1L,
                Instant.now().plusSeconds(120)
        ));
    }

    @Test
    void shouldRecoverMissionAfterSimulatedRestart() {
        mockSuccessfulRecovery();

        coordinator.recoverAvailableMissions();

        InOrder order = inOrder(
                missionClient,
                executionService
        );

        order.verify(missionClient)
                .getRecoverableMissions();

        order.verify(missionClient).claimRecovery(
                missionId,
                executionId,
                newInstance.getInstanceId()
        );

        order.verify(executionService)
                .resumeMission(any(MissionReadyPayload.class));

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertTrue(registry.activeExecutionIds().isEmpty());

        verify(payloadStore).get(executionId);

        verify(payloadStore, never())
                .remove(executionId);

        verify(payloadStore, never())
                .remove(any(MissionResultPayloadStore.PendingResult.class));
    }

    @Test
    void shouldNotRecoverWhenNoMissionsAvailable() {
        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of());

        coordinator.recoverAvailableMissions();

        verify(missionClient, never()).claimRecovery(
                any(), any(), any()
        );

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldNotRecoverCompletedMission() {
        mockSuccessfulRecovery();

        assertTrue(tracker.tryStart(executionId));
        assertTrue(tracker.complete(executionId));

        coordinator.recoverAvailableMissions();

        verify(missionClient, never()).claimRecovery(
                any(), any(), any()
        );

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldNotRecoverMissionAlreadyProcessing() {
        mockSuccessfulRecovery();

        assertTrue(tracker.tryStart(executionId));

        coordinator.recoverAvailableMissions();

        verify(missionClient, never()).claimRecovery(
                any(), any(), any()
        );

        verifyNoInteractions(executionService);
    }

    @Test
    void shouldNotResumeWhenRecoveryClaimFails() {
        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of(recoverableMission()));

        when(missionClient.claimRecovery(
                missionId,
                executionId,
                newInstance.getInstanceId()
        )).thenThrow(
                new IllegalStateException("Recovery rejected")
        );

        coordinator.recoverAvailableMissions();

        verify(executionService, never())
                .resumeMission(any());

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                tracker.getStatus(executionId)
        );
    }

    @Test
    void shouldIgnoreDuplicateRecoveryAfterCompletion() {
        mockSuccessfulRecovery();

        coordinator.recoverAvailableMissions();
        coordinator.recoverAvailableMissions();

        verify(missionClient, times(1)).claimRecovery(
                missionId,
                executionId,
                newInstance.getInstanceId()
        );

        verify(executionService, times(1))
                .resumeMission(any(MissionReadyPayload.class));
    }
}
