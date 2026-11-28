package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionHeartbeatResponse;
import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;
import uav_gateway_simulator.telemetry.FlightLifecycleService;
import uav_gateway_simulator.telemetry.FlightOwnershipService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import org.mockito.ArgumentCaptor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionHeartbeatSchedulerTest {

    private MissionClient missionClient;
    private ScheduledExecutorService executor;
    private MissionHeartbeatScheduler scheduler;
    private MissionCancellationService cancellationService;
    private FlightLifecycleService flightLifecycleService;

    private FlightOwnershipToken ownershipToken;
    private UUID missionId;
    private UUID executionId;
    private RecoveryLeaseRegistry leaseRegistry;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);

        executor = Executors.newScheduledThreadPool(2);

        cancellationService =
                mock(MissionCancellationService.class);

        flightLifecycleService =
                mock(FlightLifecycleService.class);

        leaseRegistry = new RecoveryLeaseRegistry();

        scheduler = new MissionHeartbeatScheduler(
                missionClient,
                executor,
                leaseRegistry,
                cancellationService,
                flightLifecycleService,
                new FlightOwnershipService(leaseRegistry)
        );

        ReflectionTestUtils.setField(
                scheduler,
                "heartbeatIntervalMs",
                20L
        );

        ReflectionTestUtils.setField(
                scheduler,
                "heartbeatFailureThreshold",
                3
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        ownershipToken = new FlightOwnershipToken(executionId, null, null);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        scheduler.stop(executionId);
        executor.shutdownNow();

        assertTrue(
                executor.awaitTermination(5, TimeUnit.SECONDS)
        );
    }

    private MissionHeartbeatResponse successResponse() {
        return new MissionHeartbeatResponse(
                missionId,
                executionId,
                "EXECUTING",
                Instant.now()
        );
    }

    @Test
    void shouldSendHeartbeatPeriodically() throws Exception {

        CountDownLatch latch = new CountDownLatch(3);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {
                    latch.countDown();
                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "Expected at least 3 heartbeats"
        );

        scheduler.stop(executionId);

        verify(missionClient, atLeast(3))
                .sendHeartbeat(missionId, executionId);
    }


    @Test
    void shouldNotCreateDuplicateHeartbeatSchedules()
            throws Exception {

        CountDownLatch heartbeatStarted = new CountDownLatch(1);
        CountDownLatch releaseHeartbeat = new CountDownLatch(1);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {
                    heartbeatStarted.countDown();

                    if (!releaseHeartbeat.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out waiting to release heartbeat"
                        );
                    }

                    return successResponse();
                });

        try {
            // Start lần đầu
            scheduler.start(missionId, executionId);

            assertTrue(
                    heartbeatStarted.await(5, TimeUnit.SECONDS),
                    "Heartbeat should start"
            );

            // Start lần thứ hai với cùng executionId
            scheduler.start(missionId, executionId);

            // Task đầu tiên đang chạy và bị chặn bởi latch.
            // Nếu Scheduler tạo lịch trùng, sẽ có thêm task
            // được đăng ký trong executor.
            assertEquals(
                    0,
                    ((java.util.concurrent.ScheduledThreadPoolExecutor)
                            executor).getQueue().size(),
                    "Duplicate heartbeat schedule detected"
            );

        } finally {
            releaseHeartbeat.countDown();
            scheduler.stop(executionId);
        }
    }


    @Test
    void shouldStopSendingHeartbeat() throws Exception {

        CountDownLatch latch = new CountDownLatch(1);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {
                    latch.countDown();
                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        scheduler.stop(executionId);

        // Chờ một chu kỳ đang chạy kết thúc
        Thread.sleep(100);

        int countAfterStop =
                mockingDetails(missionClient)
                        .getInvocations()
                        .size();

        Thread.sleep(100);

        assertEquals(
                countAfterStop,
                mockingDetails(missionClient)
                        .getInvocations()
                        .size()
        );
    }

    @Test
    void shouldContinueAfterHeartbeatFailure() throws Exception {

        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    int attempt = attempts.incrementAndGet();
                    latch.countDown();

                    if (attempt == 1) {
                        throw new RuntimeException(
                                "Simulated heartbeat failure"
                        );
                    }

                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "Scheduler should retry after failure"
        );

        scheduler.stop(executionId);

        assertTrue(attempts.get() >= 2);

        verify(missionClient, atLeast(2))
                .sendHeartbeat(missionId, executionId);
    }
    @Test
    void shouldStopHeartbeatWhenMissionCancelled() throws Exception {

        CountDownLatch cancellationChecked = new CountDownLatch(1);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenThrow(
                        new HttpClientErrorException(
                                HttpStatus.BAD_REQUEST
                        )
                );

        when(cancellationService.isCancelled(missionId))
                .thenAnswer(invocation -> {
                    cancellationChecked.countDown();
                    return true;
                });

        scheduler.start(missionId, executionId);

        assertTrue(
                cancellationChecked.await(5, TimeUnit.SECONDS),
                "Expected cancellation check after HTTP 400"
        );

        // Đồng bộ với executor để nhánh xử lý HTTP 400 hoàn tất.
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);

        verify(cancellationService, times(1))
                .isCancelled(missionId);

        verify(missionClient, times(1))
                .sendHeartbeat(missionId, executionId);

        verify(flightLifecycleService, timeout(5000).times(1))
                .onCancelled(missionId, executionId, ownershipToken);

        // Chờ thêm vài chu kỳ heartbeat để xác nhận đã dừng.
        Thread.sleep(100);

        verify(missionClient, times(1))
                .sendHeartbeat(missionId, executionId);
    }

    @Test
    void shouldMarkDisconnectedAfterThreeFailures() {

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenThrow(
                        new ResourceAccessException(
                                "Connection refused"
                        )
                );

        scheduler.start(missionId, executionId);

        verify(flightLifecycleService, timeout(5000).times(1))
                .onDisconnected(missionId, executionId, ownershipToken);
    }

    @Test
    void shouldNotDisconnectBeforeThreshold() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(4);

        AtomicInteger attempts = new AtomicInteger();

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    int attempt = attempts.incrementAndGet();

                    latch.countDown();

                    if (attempt <= 2) {
                        throw new ResourceAccessException(
                                "Temporary connection failure"
                        );
                    }

                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        scheduler.stop(executionId);

        verify(flightLifecycleService, never())
                .onDisconnected(any(), any(), any(FlightOwnershipToken.class));
    }

    @Test
    void shouldResetFailureCountAfterSuccessfulHeartbeat()
            throws Exception {

        AtomicInteger attempts = new AtomicInteger();

        CountDownLatch latch = new CountDownLatch(6);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    int attempt = attempts.incrementAndGet();

                    latch.countDown();

                    if (attempt == 1
                            || attempt == 2
                            || attempt == 4
                            || attempt == 5) {

                        throw new ResourceAccessException(
                                "Temporary network failure"
                        );
                    }

                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        scheduler.stop(executionId);

        verify(flightLifecycleService, never())
                .onDisconnected(any(), any(), any(FlightOwnershipToken.class));
    }

    @Test
    void shouldNotifyDisconnectedOnlyOnceDuringContinuousFailure()
            throws Exception {

        CountDownLatch latch = new CountDownLatch(6);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    latch.countDown();

                    throw new ResourceAccessException(
                            "Connection unavailable"
                    );
                });

        scheduler.start(missionId, executionId);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        scheduler.stop(executionId);

        verify(flightLifecycleService, times(1))
                .onDisconnected(missionId, executionId, ownershipToken);
    }

    @Test
    void shouldNotMarkDisconnectedWhenHeartbeatIsForbidden()
            throws Exception {

        CountDownLatch latch = new CountDownLatch(1);

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    latch.countDown();

                    throw new HttpClientErrorException(
                            HttpStatus.FORBIDDEN
                    );
                });

        scheduler.start(missionId, executionId);

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        Thread.sleep(100);

        verify(missionClient, times(1))
                .sendHeartbeat(missionId, executionId);

        verify(flightLifecycleService, never())
                .onDisconnected(any(), any(), any(FlightOwnershipToken.class));
    }

    @Test
    void shouldNotifyReconnectAfterDisconnected() {

        AtomicInteger attempts = new AtomicInteger();

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    if (attempts.incrementAndGet() <= 3) {

                        throw new ResourceAccessException(
                                "Connection unavailable"
                        );
                    }

                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        verify(flightLifecycleService, timeout(5000).times(1))
                .onDisconnected(missionId, executionId, ownershipToken);

        verify(flightLifecycleService, timeout(5000).atLeastOnce())
                .onReconnected(missionId, executionId, ownershipToken);

        scheduler.stop(executionId);

        var order = inOrder(flightLifecycleService);

        order.verify(flightLifecycleService, times(1))
                .onDisconnected(missionId, executionId, ownershipToken);

        order.verify(flightLifecycleService, atLeastOnce())
                .onReconnected(missionId, executionId, ownershipToken);
    }

    @Test
    void shouldDetectSecondDisconnectionAfterReconnect() {

        AtomicInteger attempts = new AtomicInteger();

        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {

                    int attempt = attempts.incrementAndGet();

                    if (attempt <= 3
                            || (attempt >= 5 && attempt <= 7)) {

                        throw new ResourceAccessException(
                                "Network unavailable"
                        );
                    }

                    return successResponse();
                });

        scheduler.start(missionId, executionId);

        verify(flightLifecycleService, timeout(5000).times(2))
                .onDisconnected(missionId, executionId, ownershipToken);

        verify(flightLifecycleService, atLeastOnce())
                .onReconnected(missionId, executionId, ownershipToken);

        scheduler.stop(executionId);
    }


    @Test
    void shouldStopHeartbeatAfterOwnershipLost()
            throws Exception {

        CountDownLatch firstHeartbeat =
                new CountDownLatch(1);

        CountDownLatch releaseHeartbeat =
                new CountDownLatch(1);

        when(missionClient.sendHeartbeat(
                missionId,
                executionId
        )).thenAnswer(invocation -> {

            firstHeartbeat.countDown();

            if (!releaseHeartbeat.await(
                    5, TimeUnit.SECONDS
            )) {
                throw new IllegalStateException(
                        "Heartbeat test timed out"
                );
            }

            return successResponse();
        });

        try {
            scheduler.start(missionId, executionId);

            assertTrue(
                    firstHeartbeat.await(
                            5, TimeUnit.SECONDS
                    )
            );

            leaseRegistry.markLost(executionId);

            releaseHeartbeat.countDown();

            Thread.sleep(200);

            verify(missionClient, times(1))
                    .sendHeartbeat(missionId, executionId);

            verify(flightLifecycleService, never())
                    .onReconnected(any(), any(), any(FlightOwnershipToken.class));

        } finally {

            releaseHeartbeat.countDown();

            scheduler.stop(executionId);
        }
    }


    @Test
    void shouldReplaceHeartbeatWhenGenerationChanges()
            throws Exception {

        UUID instanceId = UUID.randomUUID();

        leaseRegistry.register(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                )
        );

        AtomicInteger attempts = new AtomicInteger();

        CountDownLatch oldHeartbeatStarted =
                new CountDownLatch(1);

        CountDownLatch releaseOldHeartbeat =
                new CountDownLatch(1);

        CountDownLatch newHeartbeatStarted =
                new CountDownLatch(1);

        when(missionClient.sendHeartbeat(
                missionId,
                executionId
        )).thenAnswer(invocation -> {

            int attempt = attempts.incrementAndGet();

            if (attempt == 1) {

                oldHeartbeatStarted.countDown();

                if (!releaseOldHeartbeat.await(
                        5, TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "Old heartbeat timed out"
                    );
                }

            } else {

                newHeartbeatStarted.countDown();
            }

            return successResponse();
        });

        try {
            scheduler.start(missionId, executionId);

            assertTrue(
                    oldHeartbeatStarted.await(
                            5, TimeUnit.SECONDS
                    )
            );

            leaseRegistry.register(
                    new MissionRecoveryClaimResponse(
                            missionId,
                            executionId,
                            instanceId,
                            2L,
                            Instant.now().plusSeconds(120)
                    )
            );

            scheduler.start(missionId, executionId);

            assertTrue(
                    newHeartbeatStarted.await(
                            5, TimeUnit.SECONDS
                    ),
                    "New generation should start its heartbeat"
            );

            releaseOldHeartbeat.countDown();

            int before = attempts.get();

            Thread.sleep(200);

            assertTrue(
                    attempts.get() > before,
                    "Old session must not stop the new session"
            );

        } finally {

            releaseOldHeartbeat.countDown();

            scheduler.stop(executionId);
        }
    }


    // Capture scheduled callbacks so each test controls exactly when a tick runs.
    private ScheduledExecutorService controlledHeartbeatExecutor() {
        ScheduledExecutorService controlled = mock(ScheduledExecutorService.class);
        when(controlled.scheduleWithFixedDelay(
                any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> mock(ScheduledFuture.class));
        scheduler = new MissionHeartbeatScheduler(
                missionClient, controlled, leaseRegistry, cancellationService,
                flightLifecycleService, new FlightOwnershipService(leaseRegistry));
        ReflectionTestUtils.setField(scheduler, "heartbeatIntervalMs", 20L);
        ReflectionTestUtils.setField(scheduler, "heartbeatFailureThreshold", 1);
        return controlled;
    }

    private Runnable latestTick(ScheduledExecutorService controlled, int count) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(controlled, times(count)).scheduleWithFixedDelay(
                captor.capture(), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS));
        return captor.getValue();
    }

    private FlightOwnershipToken registerLease(UUID instanceId, long generation,
                                               Instant until) {
        leaseRegistry.register(new MissionRecoveryClaimResponse(
                missionId, executionId, instanceId, generation, until));
        return new FlightOwnershipToken(executionId, instanceId, generation);
    }

    private void assertStaleHeartbeatIgnored(RuntimeException failure,
                                             boolean sameToken,
                                             boolean expireLease) throws Exception {
        ScheduledExecutorService controlled = controlledHeartbeatExecutor();
        UUID instanceId = UUID.randomUUID();
        FlightOwnershipToken oldToken = registerLease(instanceId, 1L,
                Instant.now().plusSeconds(120));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(missionClient.sendHeartbeat(missionId, executionId))
                .thenAnswer(invocation -> {
                    if (calls.incrementAndGet() == 1) {
                        started.countDown();
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Old heartbeat timed out");
                        }
                        if (failure != null) throw failure;
                    }
                    return successResponse();
                });
        when(cancellationService.isCancelled(missionId)).thenReturn(true);
        scheduler.start(missionId, executionId, oldToken);
        Runnable oldTick = latestTick(controlled, 1);
        Future<?> oldWork = executor.submit(oldTick);
        FlightOwnershipToken newToken = oldToken;
        Runnable newTick = null;
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            if (expireLease) {
                registerLease(instanceId, 1L, Instant.now().minusSeconds(10));
            } else {
                if (sameToken) {
                    scheduler.stop(oldToken);
                } else {
                    newToken = registerLease(instanceId, 2L,
                            Instant.now().plusSeconds(120));
                }
                scheduler.start(missionId, executionId, newToken);
                newTick = latestTick(controlled, 2);
            }
        } finally {
            release.countDown();
        }
        oldWork.get(5, TimeUnit.SECONDS);
        verifyNoInteractions(flightLifecycleService);
        verify(cancellationService, never()).isCancelled(missionId);
        if (expireLease) {
            assertThrows(IllegalStateException.class,
                    () -> leaseRegistry.getForWrite(executionId));
            oldTick.run();
            verify(missionClient, times(1)).sendHeartbeat(missionId, executionId);
        } else {
            assertNotNull(newTick);
            newTick.run();
            verify(flightLifecycleService, times(1))
                    .onReconnected(missionId, executionId, newToken);
            verifyNoMoreInteractions(flightLifecycleService);
            verify(missionClient, times(2)).sendHeartbeat(missionId, executionId);
            assertEquals(newToken.recoveryGeneration(),
                    leaseRegistry.getForWrite(executionId).recoveryGeneration());
        }
    }

    @Test
    void shouldIgnoreStaleHeartbeatReconnect() throws Exception {
        assertStaleHeartbeatIgnored(null, false, false);
    }

    @Test
    void shouldIgnoreStaleHeartbeatDisconnect() throws Exception {
        assertStaleHeartbeatIgnored(new ResourceAccessException("Network unavailable"),
                false, false);
    }

    @Test
    void shouldIgnoreStaleHeartbeatCancellation() throws Exception {
        assertStaleHeartbeatIgnored(new HttpClientErrorException(HttpStatus.BAD_REQUEST),
                false, false);
    }

    @Test
    void shouldIgnoreReplacedSessionWithSameOwnershipToken() throws Exception {
        assertStaleHeartbeatIgnored(null, true, false);
    }

    @Test
    void shouldIgnoreHeartbeatResponseAfterLeaseExpires() throws Exception {
        assertStaleHeartbeatIgnored(null, false, true);
    }


    // The controlled scheduler lets us run exactly one heartbeat per worker.
    private void awaitGate(CountDownLatch gate) throws InterruptedException {
        assertTrue(gate.await(5, TimeUnit.SECONDS), "Concurrency gate timed out");
    }

    @Test
    void shouldStartWhileOwnershipUpdateIsInProgress() throws Exception {
        ScheduledExecutorService controlled = controlledHeartbeatExecutor();
        FlightOwnershipService ownership = new FlightOwnershipService(leaseRegistry);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch startAttempted = new CountDownLatch(1);
        Future<?> update = executor.submit(() -> ownership.executeIfCurrent(
                ownershipToken, () -> {
                    entered.countDown();
                    try { awaitGate(release); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                    // Reentrant registry acquisition must be safe.
                    scheduler.start(missionId, executionId, ownershipToken);
                }));
        Future<?> start;
        try {
            awaitGate(entered);
            start = executor.submit(() -> {
                startAttempted.countDown();
                scheduler.start(missionId, executionId, ownershipToken);
            });
            awaitGate(startAttempted);
        } finally {
            release.countDown();
        }
        update.get(5, TimeUnit.SECONDS);
        start.get(5, TimeUnit.SECONDS);
        when(missionClient.sendHeartbeat(missionId, executionId)).thenReturn(successResponse());
        latestTick(controlled, 1).run();
        verify(flightLifecycleService).onReconnected(missionId, executionId, ownershipToken);
    }

    @Test
    void shouldStopWhileTelemetryUpdateIsInProgress() throws Exception {
        ScheduledExecutorService controlled = controlledHeartbeatExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopAttempted = new CountDownLatch(1);
        when(missionClient.sendHeartbeat(missionId, executionId)).thenReturn(successResponse());
        doAnswer(invocation -> {
            entered.countDown();
            awaitGate(release);
            return null;
        }).when(flightLifecycleService).onReconnected(missionId, executionId, ownershipToken);
        scheduler.start(missionId, executionId, ownershipToken);
        Runnable tick = latestTick(controlled, 1);
        Future<?> update = executor.submit(tick);
        Future<?> stop;
        try {
            awaitGate(entered);
            stop = executor.submit(() -> {
                stopAttempted.countDown();
                scheduler.stop(ownershipToken);
            });
            awaitGate(stopAttempted);
        } finally {
            release.countDown();
        }
        update.get(5, TimeUnit.SECONDS);
        stop.get(5, TimeUnit.SECONDS);
        tick.run();
        verify(missionClient, times(1)).sendHeartbeat(missionId, executionId);
        verify(flightLifecycleService, times(1)).onReconnected(missionId, executionId, ownershipToken);
    }

    @Test
    void shouldRegisterRecoveryWhileHeartbeatUpdateIsInProgress() throws Exception {
        ScheduledExecutorService controlled = controlledHeartbeatExecutor();
        UUID instanceId = UUID.randomUUID();
        FlightOwnershipToken oldToken = registerLease(instanceId, 1L, Instant.now().plusSeconds(120));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch registrationAttempted = new CountDownLatch(1);
        when(missionClient.sendHeartbeat(missionId, executionId)).thenReturn(successResponse());
        doAnswer(invocation -> {
            entered.countDown();
            awaitGate(release);
            return null;
        }).when(flightLifecycleService).onReconnected(missionId, executionId, oldToken);
        scheduler.start(missionId, executionId, oldToken);
        Runnable oldTick = latestTick(controlled, 1);
        Future<?> update = executor.submit(oldTick);
        Future<FlightOwnershipToken> registration;
        try {
            awaitGate(entered);
            registration = executor.submit(() -> {
                registrationAttempted.countDown();
                return registerLease(instanceId, 2L, Instant.now().plusSeconds(120));
            });
            awaitGate(registrationAttempted);
        } finally {
            release.countDown();
        }
        update.get(5, TimeUnit.SECONDS);
        FlightOwnershipToken newToken = registration.get(5, TimeUnit.SECONDS);
        scheduler.start(missionId, executionId, newToken);
        oldTick.run();
        latestTick(controlled, 2).run();
        var order = inOrder(flightLifecycleService);
        order.verify(flightLifecycleService).onReconnected(missionId, executionId, oldToken);
        order.verify(flightLifecycleService).onReconnected(missionId, executionId, newToken);
        verifyNoMoreInteractions(flightLifecycleService);
        verify(missionClient, times(2)).sendHeartbeat(missionId, executionId);
    }

    @Test
    void shouldIgnoreStaleForbiddenOrConflictAfterRecoveryRegistration() throws Exception {
        // Each status uses a fresh registry and execution to isolate ownership.
        for (HttpStatus status : new HttpStatus[]{HttpStatus.FORBIDDEN, HttpStatus.CONFLICT}) {
            reset(missionClient, flightLifecycleService, cancellationService);
            leaseRegistry = new RecoveryLeaseRegistry();
            executionId = UUID.randomUUID();
            assertStaleHeartbeatIgnored(new HttpClientErrorException(status), false, false);
            scheduler.stop(executionId);
        }
    }

}
