
package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.dto.request.MissionNodeResultRequest;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

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
    private MissionCancellationService cancellationService;
    private MissionRecoveryLeaseManager leaseManager;
    private FlightOwnershipToken ownershipToken;
    private FlightLifecycleService flightLifecycleService;
    private RecoveryLeaseRegistry leaseRegistry;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        tracker = new MissionExecutionTracker();

        resultService = mock(
                MissionResultSimulationService.class
        );

        payloadStore = new MissionResultPayloadStore();

        executor = Executors.newSingleThreadScheduledExecutor();

        cancellationService = mock(
                MissionCancellationService.class
        );

        leaseManager = mock(
                MissionRecoveryLeaseManager.class
        );

        flightLifecycleService = mock(
                FlightLifecycleService.class
        );

        leaseRegistry = new RecoveryLeaseRegistry();

        when(leaseManager.getValidLeaseOrNull(any(UUID.class)))
                .thenAnswer(invocation ->
                        leaseRegistry.getForWrite(
                                invocation.getArgument(0, UUID.class)
                        )
                );

        retryScheduler = new MissionResultsRetryScheduler(
                tracker,
                resultService,
                payloadStore,
                leaseManager,
                cancellationService,
                flightLifecycleService,
                leaseRegistry,
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

        ownershipToken = new FlightOwnershipToken(
                executionId,
                null,
                null
        );

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

        payloadStore.saveIfAbsent(
                missionId,
                request,
                ownershipToken
        );
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

        when(resultService.retryResults(executionId, ownershipToken))
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
                .retryResults(executionId, ownershipToken);
    }

    @Test
    void shouldRetryAfterTemporaryFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch completed = new CountDownLatch(1);

        when(resultService.retryResults(executionId, ownershipToken))
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

        when(resultService.retryResults(executionId, ownershipToken))
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
                .retryResults(executionId, ownershipToken);

        // Should not schedule additional retries.
        retryScheduler.schedule(executionId);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verify(resultService, times(3))
                .retryResults(executionId, ownershipToken);
    }

    @Test
    void shouldIgnoreDuplicateSchedules() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(resultService.retryResults(executionId, ownershipToken))
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
                .retryResults(executionId, ownershipToken);

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

    @Test
    void shouldCancelResultsRetryBeforeSendingResults() throws Exception {

        doThrow(new MissionCancelledException("Mission cancelled"))
                .when(cancellationService)
                .checkNotCancelled(missionId);

        retryScheduler.schedule(executionId);

        // Wait for scheduled task to finish
        executor.schedule(() -> {}, 100, TimeUnit.MILLISECONDS)
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.CANCELLED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));

        verifyNoInteractions(resultService);

        verify(leaseManager)
                .removeIfCurrent(ownershipToken);

        verify(cancellationService, atLeastOnce())
                .checkNotCancelled(missionId);
        verify(flightLifecycleService, times(1))
                .onCancelled(
                        missionId,
                        executionId,
                        ownershipToken
                );
    }
    @Test
    void shouldCancelResultsRetryWhenResultServiceDetectsCancellation()
            throws Exception {

        when(resultService.retryResults(executionId, ownershipToken))
                .thenThrow(
                        new MissionCancelledException("Mission cancelled")
                );

        retryScheduler.schedule(executionId);

        executor.schedule(() -> {}, 100, TimeUnit.MILLISECONDS)
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.CANCELLED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));

        verify(resultService, times(1))
                .retryResults(executionId, ownershipToken);

        verify(leaseManager)
                .removeIfCurrent(ownershipToken);
        verify(flightLifecycleService, times(1))
                .onCancelled(
                        missionId,
                        executionId,
                        ownershipToken
                );
    }
    @Test
    void shouldNotCancelTelemetryWhenRetrySucceeds()
            throws Exception {

        CountDownLatch completed = new CountDownLatch(1);

        when(resultService.retryResults(
                executionId,
                ownershipToken
        )).thenAnswer(invocation -> {
            completed.countDown();
            return successResponse();
        });

        retryScheduler.schedule(executionId);

        assertTrue(completed.await(5, TimeUnit.SECONDS));

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertNull(payloadStore.get(executionId));

        verifyNoInteractions(flightLifecycleService);

        verify(leaseManager, times(1))
                .removeIfCurrent(ownershipToken);
    }
    @Test
    void shouldNotCancelTelemetryWhenRetryFails()
            throws Exception {

        ReflectionTestUtils.setField(
                retryScheduler,
                "maxAttempts",
                1
        );

        CountDownLatch attempted = new CountDownLatch(1);

        when(resultService.retryResults(
                executionId,
                ownershipToken
        )).thenAnswer(invocation -> {

            attempted.countDown();

            throw new IllegalStateException(
                    "Results retry failed"
            );
        });

        retryScheduler.schedule(executionId);

        assertTrue(attempted.await(5, TimeUnit.SECONDS));

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(payloadStore.get(executionId));

        verifyNoInteractions(flightLifecycleService);

        verify(leaseManager, never())
                .removeIfCurrent(ownershipToken);
    }

    private FlightOwnershipToken registerRecoveryLease(
            UUID instanceId,
            long generation,
            Instant leaseUntil
    ) {

        MissionRecoveryClaimResponse lease =
                mock(MissionRecoveryClaimResponse.class);

        when(lease.executionId())
                .thenReturn(executionId);

        when(lease.instanceId())
                .thenReturn(instanceId);

        when(lease.recoveryGeneration())
                .thenReturn(generation);

        when(lease.leaseUntil())
                .thenReturn(leaseUntil);

        leaseRegistry.register(lease);

        return new FlightOwnershipToken(
                executionId,
                instanceId,
                generation
        );
    }

    private void replacePendingOwnership(
            FlightOwnershipToken newToken
    ) {

        MissionResultPayloadStore.PendingResult pending =
                payloadStore.get(executionId);

        assertNotNull(pending);

        payloadStore.remove(pending);

        payloadStore.saveIfAbsent(
                missionId,
                pending.request(),
                newToken
        );
    }

    @Test
    void shouldNotScheduleRetryWithExpiredRecoveryLease()
            throws Exception {

        FlightOwnershipToken expiredToken =
                registerRecoveryLease(
                        UUID.randomUUID(),
                        1L,
                        Instant.now().minusSeconds(10)
                );

        replacePendingOwnership(expiredToken);

        retryScheduler.schedule(
                executionId,
                expiredToken
        );

        executor.schedule(
                () -> {},
                100,
                TimeUnit.MILLISECONDS
        ).get(5, TimeUnit.SECONDS);

        verifyNoInteractions(resultService);

        verifyNoInteractions(flightLifecycleService);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(payloadStore.get(executionId));

        assertEquals(
                expiredToken,
                payloadStore.get(executionId).ownershipToken()
        );

        verify(leaseManager, never())
                .removeIfCurrent(expiredToken);
    }

    @Test
    void shouldIgnoreStaleRetryCompletionAfterOwnershipChanges()
            throws Exception {

        UUID instanceId = UUID.randomUUID();

        FlightOwnershipToken oldToken =
                registerRecoveryLease(
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                );

        replacePendingOwnership(oldToken);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(resultService.retryResults(
                executionId,
                oldToken
        )).thenAnswer(invocation -> {

            started.countDown();

            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test timeout");
            }

            return successResponse();
        });

        retryScheduler.schedule(executionId, oldToken);

        FlightOwnershipToken newToken;

        try {

            assertTrue(started.await(5, TimeUnit.SECONDS));

            newToken = registerRecoveryLease(
                    instanceId,
                    2L,
                    Instant.now().plusSeconds(120)
            );

            replacePendingOwnership(newToken);

            tracker.markResultsRetry(executionId);

        } finally {
            release.countDown();
        }

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(payloadStore.get(executionId));

        assertEquals(
                newToken,
                payloadStore.get(executionId).ownershipToken()
        );

        assertEquals(
                2L,
                leaseRegistry.getForWrite(executionId)
                        .recoveryGeneration()
        );

        verify(resultService, times(1))
                .retryResults(executionId, oldToken);

        verify(leaseManager, never())
                .removeIfCurrent(oldToken);

        verifyNoInteractions(flightLifecycleService);
    }

    @Test
    void shouldIgnoreStaleRetryCancellationAfterOwnershipChanges()
            throws Exception {

        UUID instanceId = UUID.randomUUID();

        FlightOwnershipToken oldToken =
                registerRecoveryLease(
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                );

        replacePendingOwnership(oldToken);

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        when(resultService.retryResults(
                executionId,
                oldToken
        )).thenAnswer(invocation -> {

            started.countDown();

            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test timeout");
            }

            throw new MissionCancelledException(
                    "Stale worker cancellation"
            );
        });

        retryScheduler.schedule(executionId, oldToken);

        FlightOwnershipToken newToken;

        try {

            assertTrue(started.await(5, TimeUnit.SECONDS));

            newToken = registerRecoveryLease(
                    instanceId,
                    2L,
                    Instant.now().plusSeconds(120)
            );

            replacePendingOwnership(newToken);

            tracker.markResultsRetry(executionId);

        } finally {
            release.countDown();
        }

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                tracker.getStatus(executionId)
        );

        assertNotNull(payloadStore.get(executionId));

        assertEquals(
                newToken,
                payloadStore.get(executionId).ownershipToken()
        );

        assertEquals(
                2L,
                leaseRegistry.getForWrite(executionId)
                        .recoveryGeneration()
        );

        verify(resultService, times(1))
                .retryResults(executionId, oldToken);

        verifyNoInteractions(flightLifecycleService);

        verify(leaseManager, never())
                .removeIfCurrent(oldToken);
    }
    @Test
    void shouldCleanupRetryStateWhenOwnershipLostImmediatelyAfterStart() throws Exception {
        // Run the scheduled callback only after capturing its RetryState.
        ScheduledExecutorService controlled = mock(ScheduledExecutorService.class);
        var callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        retryScheduler = new MissionResultsRetryScheduler(tracker, resultService,
                payloadStore, leaseManager, cancellationService, flightLifecycleService,
                leaseRegistry, controlled);
        ReflectionTestUtils.setField(retryScheduler, "retryEnabled", true);
        ReflectionTestUtils.setField(retryScheduler, "retryDelayMs", 10L);
        ReflectionTestUtils.setField(retryScheduler, "maxAttempts", 3);
        retryScheduler.schedule(executionId, ownershipToken);
        verify(controlled).schedule(callback.capture(), eq(10L), eq(TimeUnit.MILLISECONDS));
        var states = (java.util.Map<?, ?>) ReflectionTestUtils.getField(retryScheduler, "retryStates");
        Object state = states.get(executionId);
        assertNotNull(state);
        AtomicInteger checks = new AtomicInteger();
        when(leaseManager.getValidLeaseOrNull(executionId)).thenAnswer(invocation -> {
            if (checks.incrementAndGet() == 2) {
                leaseRegistry.register(new MissionRecoveryClaimResponse(missionId, executionId,
                        UUID.randomUUID(), 1L, Instant.now().plusSeconds(120)));
            }
            return leaseRegistry.getForWrite(executionId);
        });
        callback.getValue().run();
        assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(state, "running"));
        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(state, "exhausted"));
        assertFalse(states.containsKey(executionId));
        verifyNoInteractions(resultService, flightLifecycleService);
        verify(leaseManager, never()).removeIfCurrent(ownershipToken);
        assertEquals(ownershipToken, payloadStore.get(executionId).ownershipToken());
    }
}
