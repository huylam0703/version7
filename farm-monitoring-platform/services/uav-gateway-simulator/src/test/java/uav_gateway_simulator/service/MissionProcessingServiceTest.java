
package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionNodeResultRequest;

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


    @BeforeEach
    void setUp() {
        executionService = mock(MissionExecutionService.class);
        tracker = new MissionExecutionTracker();
        executor = Executors.newSingleThreadExecutor();

        resultSimulationService =
                mock(MissionResultSimulationService.class);

        retryScheduler =
                mock(MissionResultsRetryScheduler.class);
        MissionClient missionClient = mock(MissionClient.class);

        MissionRecoveryLeaseManager leaseManager =
                mock(MissionRecoveryLeaseManager.class);

        SimulatorInstanceIdentity instanceIdentity =
                mock(SimulatorInstanceIdentity.class);

        RecoveryLeaseRegistry leaseRegistry =
                new RecoveryLeaseRegistry();

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
                leaseRegistry
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
    void shouldAllowRetryAfterFailure() throws Exception {
        MissionReadyPayload payload = createPayload();

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

        // Wait for worker to handle the exception
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertTrue(
                tracker.tryStart(payload.getExecutionId())
        );

        tracker.fail(payload.getExecutionId());
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

        verify(retryScheduler, times(1))
                .schedule(executionId);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(
                payloadStore.get(executionId)
        );
    }
}
