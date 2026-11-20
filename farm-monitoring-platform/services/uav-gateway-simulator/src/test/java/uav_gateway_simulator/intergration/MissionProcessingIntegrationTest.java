package uav_gateway_simulator.intergration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.config.SimulatorInstanceIdentity;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.MissionResultRequest;
import uav_gateway_simulator.dto.MissionNodeResultRequest;
import uav_gateway_simulator.service.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionProcessingIntegrationTest {

    private MissionExecutionService executionService;
    private MissionExecutionTracker tracker;
    private MissionResultPayloadStore payloadStore;
    private MissionResultsRetryScheduler retryScheduler;
    private MissionProcessingService processingService;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executionService = mock(MissionExecutionService.class);
        tracker = new MissionExecutionTracker();
        payloadStore = new MissionResultPayloadStore();
        retryScheduler = mock(MissionResultsRetryScheduler.class);
        executor = Executors.newSingleThreadExecutor();

        processingService = new MissionProcessingService(
                executionService,
                tracker,
                mock(MissionResultSimulationService.class),
                payloadStore,
                executor,
                retryScheduler,
                mock(MissionClient.class),
                mock(MissionRecoveryLeaseManager.class),
                mock(SimulatorInstanceIdentity.class),
                new RecoveryLeaseRegistry()
        );
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void shouldCompleteExecutionAndIgnoreDuplicate()
            throws Exception {

        UUID executionId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        MissionReadyPayload payload = new MissionReadyPayload();
        payload.setExecutionId(executionId);
        payload.setMissionId(missionId);

        doAnswer(invocation -> {
            // Simulate successful Results preparation.
            tracker.markResultsPending(executionId);
            return null;
        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        // Wait until the worker completes.
        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        // Submitting the same event must not execute again.
        processingService.submit(payload);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        verify(executionService, times(1))
                .prepareMission(payload);

        verify(retryScheduler, never())
                .schedule(executionId);
    }


    @Test
    void shouldRemoveResultsPayloadAfterCompletion()
            throws Exception {

        UUID executionId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        MissionReadyPayload payload = new MissionReadyPayload();
        payload.setExecutionId(executionId);
        payload.setMissionId(missionId);

        doAnswer(invocation -> {

            tracker.markResultsPending(executionId);

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

            payloadStore.saveIfAbsent(missionId, request);

            return null;

        }).when(executionService).prepareMission(payload);

        processingService.submit(payload);

        executor.submit(() -> {})
                .get(5, TimeUnit.SECONDS);

        assertEquals(
                MissionExecutionTracker.Status.COMPLETED,
                tracker.getStatus(executionId)
        );

        assertNull(
                payloadStore.get(executionId),
                "Results payload must be removed after completion"
        );

        verify(retryScheduler, never())
                .schedule(executionId);
    }

}
