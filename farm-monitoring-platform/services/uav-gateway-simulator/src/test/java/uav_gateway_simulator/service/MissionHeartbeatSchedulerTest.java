package uav_gateway_simulator.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionHeartbeatResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);

        executor = Executors.newScheduledThreadPool(2);

        scheduler = new MissionHeartbeatScheduler(
                missionClient,
                executor
        );

        ReflectionTestUtils.setField(
                scheduler,
                "heartbeatIntervalMs",
                20L
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
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
}