package uav_gateway_simulator.intergration;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.*;
import uav_gateway_simulator.service.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionExecutionIntegrationTest {

    @Test
    void shouldExecuteWaypointsAndSendResultsInOrder() {
        UUID missionId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID uavId = UUID.randomUUID();
        UUID gatewayId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        MissionReadyPayload payload = new MissionReadyPayload();
        payload.setMissionId(missionId);
        payload.setExecutionId(executionId);
        payload.setFarmId(farmId);
        payload.setUavId(uavId);
        payload.setGatewayId(gatewayId);
        payload.setAckDeadline(Instant.now().plusSeconds(120));

        WaypointResponse waypoint1 = new WaypointResponse(
                UUID.randomUUID(), 1,
                new BigDecimal("10.123"),
                new BigDecimal("106.123"),
                new BigDecimal("20"),
                "PENDING"
        );

        WaypointResponse waypoint2 = new WaypointResponse(
                UUID.randomUUID(), 2,
                new BigDecimal("10.124"),
                new BigDecimal("106.124"),
                new BigDecimal("20"),
                "PENDING"
        );

        MissionDetailResponse mission = new MissionDetailResponse(
                missionId, farmId, uavId, gatewayId,
                "Integration Test Mission",
                "test-user", null, "RUNNING",
                null, null, null,
                1, 0, 0,
                List.of(waypoint2, waypoint1),
                List.of(nodeId)
        );

        MissionClient client = mock(MissionClient.class);
        MissionExecutionTracker tracker = new MissionExecutionTracker();
        RecoveryLeaseRegistry registry = new RecoveryLeaseRegistry();
        MissionResultPayloadStore store = new MissionResultPayloadStore();

        WaypointSimulationService waypointService =
                new WaypointSimulationService(client, registry);

        ReflectionTestUtils.setField(
                waypointService, "waypointDelayMs", 0L
        );

        MissionResultSimulationService resultService =
                new MissionResultSimulationService(
                        client, store, registry
                );

        MissionHeartbeatScheduler heartbeat =
                mock(MissionHeartbeatScheduler.class);

        MissionResumePlanner resumePlanner =
                mock(MissionResumePlanner.class);

        MissionRecoveryLeaseManager leaseManager =
                mock(MissionRecoveryLeaseManager.class);

        MissionExecutionService executionService =
                new MissionExecutionService(
                        client,
                        waypointService,
                        heartbeat,
                        resultService,
                        tracker,
                        resumePlanner,
                        leaseManager
                );

        when(client.getMissionDetail(missionId))
                .thenReturn(mission);

        when(client.ackMission(missionId, executionId))
                .thenReturn(new MissionAckResponse(
                        missionId, executionId,
                        "EXECUTING", Instant.now()
                ));


        when(client.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenAnswer(invocation -> {

            MissionProgressRequest request =
                    invocation.getArgument(1);

            return new MissionProgressResponse(
                    missionId,
                    executionId,
                    "EXECUTING",
                    request.sequence(),
                    request.waypointSeq(),
                    Instant.now()
            );
        });


        when(client.sendResults(
                eq(missionId), any(MissionResultRequest.class)
        )).thenReturn(new MissionResultResponse(
                missionId, executionId,
                "COMPLETED", 1, 1, 0
        ));

        assertTrue(tracker.tryStart(executionId));

        assertDoesNotThrow(
                () -> executionService.prepareMission(payload)
        );

        var order = inOrder(client);

        order.verify(client).getMissionDetail(missionId);
        order.verify(client).ackMission(missionId, executionId);

        order.verify(client).updateProgress(
                eq(missionId),
                argThat(request ->
                        request.sequence() == 1
                                && request.waypointSeq() == 1
                )
        );

        order.verify(client).updateProgress(
                eq(missionId),
                argThat(request ->
                        request.sequence() == 2
                                && request.waypointSeq() == 2
                )
        );

        order.verify(client).sendResults(
                eq(missionId),
                any(MissionResultRequest.class)
        );

        verify(heartbeat).start(missionId, executionId);
        verify(heartbeat).stop(executionId);

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_PENDING,
                tracker.getStatus(executionId)
        );

        assertNotNull(store.get(executionId));
    }
}
