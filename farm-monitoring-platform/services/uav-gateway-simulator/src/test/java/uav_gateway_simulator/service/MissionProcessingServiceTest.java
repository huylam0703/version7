
package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.dto.request.MissionNodeResultRequest;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionProcessingServiceTest {

    private MissionExecutionService executionService;
    private MissionExecutionTracker tracker;
    private ExecutorService executor;
    private MissionProcessingService processingService;
    private MissionResultsRetryScheduler retryScheduler;
    private MissionResultPayloadStore payloadStore;
    private MissionResultSimulationService resultSimulationService;
    private MissionClient missionClient;
    private MissionRecoveryLeaseManager leaseManager;
    private SimulatorInstanceIdentity instanceIdentity;
    private RecoveryLeaseRegistry leaseRegistry;


    @BeforeEach
    void setUp() {
        executionService = mock(MissionExecutionService.class);
        tracker = new MissionExecutionTracker();
        executor = Executors.newSingleThreadExecutor();

        resultSimulationService =
                mock(MissionResultSimulationService.class);

        retryScheduler =
                mock(MissionResultsRetryScheduler.class);
        missionClient = mock(MissionClient.class);
        leaseManager = mock(MissionRecoveryLeaseManager.class);
        instanceIdentity = mock(SimulatorInstanceIdentity.class);
        leaseRegistry = new RecoveryLeaseRegistry();
        FlightLifecycleService flightLifecycleService =
                mock(FlightLifecycleService.class);

        payloadStore = new MissionResultPayloadStore();

        processingService = new MissionProcessingService(
                executionService,
                tracker,
                resultSimulationService,
                payloadStore,
                executor,
                retryScheduler,
                missionClient,
                leaseManager,
                instanceIdentity,
                leaseRegistry,
                flightLifecycleService
        );
    }


    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();

        assertTrue(
                executor.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                )
        );
    }

    private MissionReadyPayload createPayload() {
        MissionReadyPayload payload = new MissionReadyPayload();

        payload.setExecutionId(UUID.randomUUID());
        payload.setMissionId(UUID.randomUUID());

        return payload;
    }

    @Test
    void shouldProcessExecutionAsynchronously() throws Exception {
        MissionReadyPayload payload = createPayload();

        CountDownLatch completed = new CountDownLatch(1);

        doAnswer(invocation -> {
            completed.countDown();
            return null;
        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        assertTrue(
                completed.await(5, TimeUnit.SECONDS)
        );

        verify(executionService, times(1))
                .prepareMission(payload);
    }

    @Test
    void shouldIgnoreDuplicateExecution() throws Exception {
        MissionReadyPayload payload = createPayload();

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        doAnswer(invocation -> {
            started.countDown();

            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Test timeout"
                    );
                }
            } finally {
                finished.countDown();
            }

            return null;
        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        try {
            assertTrue(
                    started.await(5, TimeUnit.SECONDS)
            );

            // Duplicate while execution is processing
            processingService.submit(payload);

        } finally {
            release.countDown();
        }

        assertTrue(
                finished.await(5, TimeUnit.SECONDS)
        );

        // Wait for worker to finish updating tracker
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        // Duplicate after execution completed
        processingService.submit(payload);

        verify(executionService, times(1))
                .prepareMission(payload);

        assertFalse(
                tracker.tryStart(payload.getExecutionId())
        );
    }


    @Test
    void shouldRequireRecoveryAfterFailure() throws Exception {
        MissionReadyPayload payload = createPayload();
        UUID executionId = payload.getExecutionId();

        CountDownLatch failed = new CountDownLatch(1);

        doAnswer(invocation -> {
            try {
                throw new IllegalStateException(
                        "Simulated failure"
                );
            } finally {
                failed.countDown();
            }
        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        assertTrue(
                failed.await(5, TimeUnit.SECONDS)
        );

        // Wait for worker to finish exception handling.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        // Execution must be marked FAILED.
        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                tracker.getStatus(executionId)
        );

        // Normal submit must not restart failed execution.
        assertFalse(
                tracker.tryStart(executionId)
        );

        // Recovery is allowed to claim a FAILED execution.
        assertTrue(
                tracker.tryStartRecovery(executionId)
        );

        assertEquals(
                MissionExecutionTracker.Status.PROCESSING,
                tracker.getStatus(executionId)
        );

        // Restore previous status when recovery is rolled back.
        assertTrue(
                tracker.rollbackRecovery(executionId)
        );

        assertEquals(
                MissionExecutionTracker.Status.FAILED,
                tracker.getStatus(executionId)
        );

        verify(executionService, times(1))
                .prepareMission(payload);
    }


    @Test
    void shouldNotScheduleRetryWhenPayloadMissing()
            throws Exception {

        MissionReadyPayload payload = createPayload();
        UUID executionId = payload.getExecutionId();

        CountDownLatch failed = new CountDownLatch(1);

        doAnswer(invocation -> {

            // Simulate completed Waypoints
            tracker.markResultsPending(executionId);

            // Simulate failed Mission Results
            tracker.markResultsRetry(executionId);

            failed.countDown();

            throw new IllegalStateException(
                    "Simulated Mission Results failure"
            );

        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        assertTrue(
                failed.await(5, TimeUnit.SECONDS)
        );

        // Wait for worker to handle failure
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNull(
                payloadStore.get(executionId)
        );

        verify(retryScheduler, never())
                .schedule(executionId);
    }

    @Test
    void shouldScheduleRetryWhenPayloadExists()
            throws Exception {

        MissionReadyPayload payload = createPayload();
        UUID executionId = payload.getExecutionId();
        UUID nodeId = UUID.randomUUID();

        CountDownLatch failed = new CountDownLatch(1);

        doAnswer(invocation -> {

            // Simulate completed Waypoints
            tracker.markResultsPending(executionId);

            // Store Mission Results payload
            MissionNodeResultRequest nodeResult =
                    new MissionNodeResultRequest(
                            nodeId,
                            "SUCCESS",
                            10,
                            1,
                            Instant.now(),
                            null,
                            null
                    );

            MissionResultRequest request =
                    new MissionResultRequest(
                            executionId,
                            List.of(nodeResult),
                            true
                    );

            payloadStore.saveIfAbsent(
                    payload.getMissionId(),
                    request
            );

            // Simulate failed Mission Results
            tracker.markResultsRetry(executionId);

            failed.countDown();

            throw new IllegalStateException(
                    "Simulated Mission Results failure"
            );

        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        assertTrue(
                failed.await(5, TimeUnit.SECONDS)
        );

        // Wait for worker to handle failure
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verify(retryScheduler).schedule(
                executionId,
                new FlightOwnershipToken(
                        executionId,
                        null,
                        null
                )
        );

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(
                payloadStore.get(executionId)
        );
    }
    @Test
    void shouldMarkExecutionCancelledAndNotScheduleRetry()
            throws Exception {

        MissionReadyPayload payload = createPayload();
        UUID executionId = payload.getExecutionId();

        doThrow(new MissionCancelledException("Mission cancelled"))
                .when(executionService)
                .prepareMission(payload);

        // Submit mission for asynchronous processing
        processingService.submit(payload);

        // Wait for the worker to finish exception handling
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        // Execution must be marked CANCELLED
        assertEquals(
                MissionExecutionTracker.Status.CANCELLED,
                tracker.getStatus(executionId)
        );

        // No pending Results payload
        assertNull(payloadStore.get(executionId));

        // Cancellation must not trigger Results Retry
        verify(retryScheduler, never())
                .schedule(any(UUID.class));

        // Mission execution was attempted exactly once
        verify(executionService, times(1))
                .prepareMission(payload);

        // Cancelled execution must not be restarted
        assertFalse(tracker.tryStart(executionId));

        // Cancelled execution must not be recovered
        assertFalse(tracker.tryStartRecovery(executionId));
    }
    @Test
    void shouldRemovePendingResultsPayloadWhenCancelled()
            throws Exception {

        MissionReadyPayload payload = createPayload();

        UUID executionId = payload.getExecutionId();
        UUID nodeId = UUID.randomUUID();

        MissionNodeResultRequest nodeResult =
                new MissionNodeResultRequest(
                        nodeId,
                        "SUCCESS",
                        10,
                        1,
                        Instant.now(),
                        null,
                        null
                );

        MissionResultRequest request =
                new MissionResultRequest(
                        executionId,
                        List.of(nodeResult),
                        true
                );

        payloadStore.saveIfAbsent(
                payload.getMissionId(),
                request
        );

        assertNotNull(payloadStore.get(executionId));

        doThrow(new MissionCancelledException("Mission cancelled"))
                .when(executionService)
                .prepareMission(payload);

        processingService.submit(payload);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.CANCELLED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));

        verify(retryScheduler, never())
                .schedule(any(UUID.class));
    }
    @Test
    void shouldCancelRecoveryAndCleanupLease() throws Exception {

        MissionReadyPayload payload = createPayload();

        UUID missionId = payload.getMissionId();
        UUID executionId = payload.getExecutionId();
        UUID instanceId = UUID.randomUUID();

        // Recovery chỉ được bắt đầu khi execution FAILED.
        assertTrue(tracker.tryStart(executionId));
        tracker.fail(executionId);

        when(instanceIdentity.getInstanceId())
                .thenReturn(instanceId);

        MissionRecoveryClaimResponse claim =
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                );

        when(missionClient.claimRecovery(
                missionId,
                executionId,
                instanceId
        )).thenReturn(claim);
        FlightOwnershipToken ownershipToken =
                new FlightOwnershipToken(
                        executionId,
                        instanceId,
                        claim.recoveryGeneration()
                );

        doAnswer(invocation -> {
            leaseRegistry.register(claim);
            return null;
        }).when(leaseManager).register(claim);

        when(leaseManager.removeIfCurrent(ownershipToken))
                .thenAnswer(invocation ->
                        leaseRegistry.removeAfterCompletionIfCurrent(
                                ownershipToken
                        )
                );

        // Mission bị hủy trong quá trình Resume.
        doThrow(new MissionCancelledException("Mission cancelled"))
                .when(executionService)
                .resumeMission(payload);

        assertTrue(processingService.recover(payload));

        // Đợi worker xử lý xong Cancellation.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.CANCELLED,
                tracker.getStatus(executionId)
        );

        verify(leaseManager).register(claim);
        verify(leaseManager)
                .removeIfCurrent(ownershipToken);

        assertTrue(
                leaseRegistry.activeExecutionIds().isEmpty()
        );

        verify(executionService).resumeMission(payload);

        verify(retryScheduler, never())
                .schedule(any(UUID.class));

        assertNull(payloadStore.get(executionId));

        assertFalse(tracker.tryStartRecovery(executionId));
    }
}
