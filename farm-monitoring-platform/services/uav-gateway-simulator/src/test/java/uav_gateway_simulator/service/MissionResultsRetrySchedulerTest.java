
package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionNodeResultRequest;
import uav_gateway_simulator.dto.MissionResultResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionResultsRetrySchedulerTest {

    private MissionExecutionTracker tracker;
    private MissionResultSimulationService resultService;
    private MissionResultPayloadStore payloadStore;
    private ScheduledExecutorService executor;
    private MissionResultsRetryScheduler retryScheduler;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        tracker = new MissionExecutionTracker();
        resultService = mock(MissionResultSimulationService.class);
        payloadStore = new MissionResultPayloadStore();

        executor = Executors.newSingleThreadScheduledExecutor();

        retryScheduler = new MissionResultsRetryScheduler(
                tracker,
                resultService,
                payloadStore,
                executor
        );

        ReflectionTestUtils.setField(
                retryScheduler, "retryEnabled", true
        );
        ReflectionTestUtils.setField(
                retryScheduler, "retryDelayMs", 10L
        );
        ReflectionTestUtils.setField(
                retryScheduler, "maxAttempts", 3
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        assertTrue(tracker.tryStart(executionId));
        tracker.markResultsPending(executionId);
        tracker.markResultsRetry(executionId);

        UUID nodeId = UUID.randomUUID();

        MissionResultRequest request = new MissionResultRequest(
                executionId,
                List.of(new MissionNodeResultRequest(
                        nodeId,
                        "SUCCESS",
                        10,
                        1,
                        Instant.now(),
                        null,
                        null
                )),
                true
        );

        payloadStore.saveIfAbsent(missionId, request);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(
                5, TimeUnit.SECONDS
        ));
    }

    private MissionResultResponse successResponse() {
        return new MissionResultResponse(
                missionId,
                executionId,
                "COMPLETED",
                1,
                1,
                0
        );
    }

    @Test
    void shouldRetrySuccessfully() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);

        when(resultService.retryResults(executionId))
                .thenAnswer(invocation -> {
                    completed.countDown();
                    return successResponse();
                });

        retryScheduler.schedule(executionId);

        assertTrue(completed.await(5, TimeUnit.SECONDS));

        // Wait for scheduled task to finish.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));

        verify(resultService, times(1))
                .retryResults(executionId);
    }

    @Test
    void shouldRetryAfterTemporaryFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);

        when(resultService.retryResults(executionId))
                .thenAnswer(invocation -> {
                    int attempt = attempts.incrementAndGet();

                    if (attempt == 1) {
                        throw new IllegalStateException(
                                "Temporary failure"
                        );
                    }

                    completed.countDown();
                    return successResponse();
                });

        retryScheduler.schedule(executionId);

        assertTrue(completed.await(5, TimeUnit.SECONDS));

        // Wait until tracker is updated.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(2, attempts.get());

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));
    }

    @Test
    void shouldStopAfterMaxAttempts() throws Exception {
        CountDownLatch attempts = new CountDownLatch(3);

        when(resultService.retryResults(executionId))
                .thenAnswer(invocation -> {
                    attempts.countDown();
                    throw new IllegalStateException(
                            "Persistent failure"
                    );
                });

        retryScheduler.schedule(executionId);

        assertTrue(attempts.await(5, TimeUnit.SECONDS));

        // Wait for final retry to finish.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(payloadStore.get(executionId));

        verify(resultService, times(3))
                .retryResults(executionId);

        // Should not schedule additional retries.
        retryScheduler.schedule(executionId);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verify(resultService, times(3))
                .retryResults(executionId);
    }

    @Test
    void shouldIgnoreDuplicateSchedules() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(resultService.retryResults(executionId))
                .thenAnswer(invocation -> {
                    started.countDown();

                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Test timeout"
                        );
                    }

                    return successResponse();
                });

        retryScheduler.schedule(executionId);
        retryScheduler.schedule(executionId);
        retryScheduler.schedule(executionId);

        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));

            // A retry is already running.
            retryScheduler.schedule(executionId);
        } finally {
            release.countDown();
        }

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verify(resultService, times(1))
                .retryResults(executionId);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );
    }

    @Test
    void shouldNotRetryWithoutPayload() throws Exception {
        payloadStore.remove(executionId);

        retryScheduler.schedule(executionId);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verifyNoInteractions(resultService);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );
    }
}
