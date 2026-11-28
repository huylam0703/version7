
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.telemetry.FlightLifecycleService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionRecoveryRetryTest {

    private MissionClient missionClient;
    private MissionExecutionService executionService;
    private MissionResultSimulationService resultService;
    private MissionResultPayloadStore payloadStore;
    private MissionResultsRetryScheduler retryScheduler;
    private ExecutorService executor;

    private MissionExecutionTracker tracker;
    private RecoveryLeaseRegistry registry;
    private SimulatorInstanceIdentity instance;
    private MissionProcessingService processingService;

    private UUID missionId;
    private UUID executionId;
    private MissionReadyPayload payload;

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

        tracker = new MissionExecutionTracker();
        registry = new RecoveryLeaseRegistry();
        instance = new SimulatorInstanceIdentity();

        MissionRecoveryLeaseManager leaseManager =
                new MissionRecoveryLeaseManager(
                        missionClient,
                        registry
                );

        processingService = new MissionProcessingService(
                executionService,
                tracker,
                resultService,
                payloadStore,
                executor,
                retryScheduler,
                missionClient,
                leaseManager,
                instance,
                registry,
                flightLifecycleService
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        payload = new MissionReadyPayload();
        payload.setMissionId(missionId);
        payload.setExecutionId(executionId);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    private MissionRecoveryClaimResponse validClaim() {
        return new MissionRecoveryClaimResponse(
                missionId,
                executionId,
                instance.getInstanceId(),
                1L,
                Instant.now().plusSeconds(120)
        );
    }

    @Test
    void shouldRetryAfterFirstClaimFails() {
        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instance.getInstanceId()
        )).thenThrow(
                new IllegalStateException("Temporary claim failure")
        ).thenAnswer(invocation -> validClaim());

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                tracker.getStatus(executionId)
        );

        // Claim failed before ownership was acquired.
        // The registry must not retain a recovery marker.
        assertNull(registry.getForWrite(executionId));

        verify(executionService, never())
                .resumeMission(any());

        assertTrue(processingService.recover(payload));

        verify(missionClient, times(2)).claimRecovery(
                missionId,
                executionId,
                instance.getInstanceId()
        );

        verify(executionService, times(1))
                .resumeMission(payload);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertTrue(registry.activeExecutionIds().isEmpty());
    }

    @Test
    void shouldCleanupWhenExecutorRejectsRecovery() {
        doThrow(
                new RejectedExecutionException("Executor unavailable")
        ).when(executor).execute(any(Runnable.class));

        assertThrows(
                RejectedExecutionException.class,
                () -> processingService.recover(payload)
        );

        assertNull(tracker.getStatus(executionId));

        // Must not leave the execution marked as recovering.
        assertNull(registry.getForWrite(executionId));

        verify(missionClient, never())
                .claimRecovery(any(), any(), any());
    }

    @Test
    void shouldRetryAfterInvalidClaimResponse() {
        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instance.getInstanceId()
        )).thenReturn(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        UUID.randomUUID(),
                        1L,
                        Instant.now().plusSeconds(120)
                )
        ).thenAnswer(invocation -> validClaim());

        assertTrue(processingService.recover(payload));

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                tracker.getStatus(executionId)
        );

        assertNull(registry.getForWrite(executionId));

        assertTrue(processingService.recover(payload));

        verify(executionService, times(1))
                .resumeMission(payload);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );
    }
}
