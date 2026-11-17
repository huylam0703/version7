
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionExecutionServiceTest {

    private MissionClient missionClient;
    private MissionExecutionService missionExecutionService;
    private WaypointSimulationService waypointSimulationService;
    private MissionHeartbeatScheduler heartbeatScheduler;
    private MissionResultSimulationService missionResultSimulationService;
    private MissionExecutionTracker executionTracker;

    private UUID missionId;
    private UUID executionId;
    private UUID farmId;
    private UUID uavId;
    private UUID gatewayId;
    private UUID nodeId;

    private MissionReadyPayload payload;
    private MissionDetailResponse mission;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);
        waypointSimulationService =
                mock(WaypointSimulationService.class);
        heartbeatScheduler =
                mock(MissionHeartbeatScheduler.class);
        missionResultSimulationService =
                mock(MissionResultSimulationService.class);
        executionTracker = new MissionExecutionTracker();

        missionExecutionService = new MissionExecutionService(
                missionClient,
                waypointSimulationService,
                heartbeatScheduler,
                missionResultSimulationService,
                executionTracker
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        assertTrue(executionTracker.tryStart(executionId));
        farmId = UUID.randomUUID();
        uavId = UUID.randomUUID();
        gatewayId = UUID.randomUUID();
        nodeId = UUID.randomUUID();

        payload = new MissionReadyPayload();
        payload.setMissionId(missionId);
        payload.setExecutionId(executionId);
        payload.setFarmId(farmId);
        payload.setUavId(uavId);
        payload.setGatewayId(gatewayId);
        payload.setAckDeadline(
                Instant.now().plusSeconds(90)
        );

        WaypointResponse waypoint = new WaypointResponse(
                UUID.randomUUID(),
                1,
                new BigDecimal("10.123"),
                new BigDecimal("106.123"),
                new BigDecimal("20"),
                "PENDING"
        );

        mission = new MissionDetailResponse(
                missionId,
                farmId,
                uavId,
                gatewayId,
                "Test Mission",
                "test-user",
                null,
                "RUNNING",
                null,
                null,
                null,
                1,
                0,
                0,
                List.of(waypoint),
                List.of(nodeId)
        );
    }

    private void stubSuccessfulAck() {
        when(missionClient.getMissionDetail(missionId))
                .thenReturn(mission);

        when(missionClient.ackMission(
                missionId,
                executionId
        )).thenReturn(
                new MissionAckResponse(
                        missionId,
                        executionId,
                        "EXECUTING",
                        Instant.now()
                )
        );
    }

    private void stubSuccessfulResults() {
        when(missionResultSimulationService.simulateResults(
                missionId,
                executionId,
                List.of(nodeId)
        )).thenReturn(
                new MissionResultResponse(
                        missionId,
                        executionId,
                        "COMPLETED",
                        1,
                        1,
                        0
                )
        );
    }

    @Test
    void shouldExecuteCompleteMissionFlowInOrder() {
        stubSuccessfulAck();
        stubSuccessfulResults();

        assertDoesNotThrow(
                () -> missionExecutionService.prepareMission(payload)
        );

        InOrder inOrder = inOrder(
                missionClient,
                heartbeatScheduler,
                waypointSimulationService,
                missionResultSimulationService
        );

        inOrder.verify(missionClient)
                .getMissionDetail(missionId);

        inOrder.verify(missionClient)
                .ackMission(missionId, executionId);

        inOrder.verify(heartbeatScheduler)
                .start(missionId, executionId);

        inOrder.verify(waypointSimulationService)
                .simulate(
                        missionId,
                        executionId,
                        mission.waypoints()
                );

        inOrder.verify(missionResultSimulationService)
                .simulateResults(
                        missionId,
                        executionId,
                        List.of(nodeId)
                );

        inOrder.verify(heartbeatScheduler)
                .stop(executionId);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldNotSendResultsWhenWaypointSimulationFails() {
        stubSuccessfulAck();

        doThrow(new IllegalStateException(
                "Waypoint simulation failed"
        )).when(waypointSimulationService)
                .simulate(
                        missionId,
                        executionId,
                        mission.waypoints()
                );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        assertEquals(
                "Waypoint simulation failed",
                exception.getMessage()
        );

        verifyNoInteractions(missionResultSimulationService);

        verify(heartbeatScheduler)
                .stop(executionId);
    }

    @Test
    void shouldStopHeartbeatWhenSendingResultsFails() {
        stubSuccessfulAck();

        when(missionResultSimulationService.simulateResults(
                missionId,
                executionId,
                List.of(nodeId)
        )).thenThrow(
                new IllegalStateException(
                        "Mission Results failed"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        assertEquals(
                "Mission Results failed",
                exception.getMessage()
        );

        verify(waypointSimulationService)
                .simulate(
                        missionId,
                        executionId,
                        mission.waypoints()
                );

        verify(heartbeatScheduler)
                .stop(executionId);
    }

    @Test
    void shouldRejectMissionWithoutNodesBeforeAck() {
        mission = new MissionDetailResponse(
                missionId,
                farmId,
                uavId,
                gatewayId,
                "Test Mission",
                "test-user",
                null,
                "RUNNING",
                null,
                null,
                null,
                0,
                0,
                0,
                mission.waypoints(),
                List.of()
        );

        when(missionClient.getMissionDetail(missionId))
                .thenReturn(mission);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        assertEquals(
                "Mission has no nodes",
                exception.getMessage()
        );

        verify(missionClient, never())
                .ackMission(any(), any());

        verifyNoInteractions(
                heartbeatScheduler,
                waypointSimulationService,
                missionResultSimulationService
        );
    }


    @Test
    void shouldRejectInvalidResultsResponse() {
        stubSuccessfulAck();

        when(missionResultSimulationService.simulateResults(
                missionId,
                executionId,
                List.of(nodeId)
        )).thenReturn(
                new MissionResultResponse(
                        missionId,
                        executionId,
                        "RUNNING",
                        1,
                        0,
                        1
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        assertTrue(
                exception.getMessage().contains(
                        "Invalid final Mission status"
                )
        );

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                executionTracker.getStatus(executionId)
        );

        verify(heartbeatScheduler)
                .stop(executionId);
    }


    @Test
    void shouldRejectExpiredAckDeadline() {
        payload.setAckDeadline(
                Instant.now().minusSeconds(1)
        );

        assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        verifyNoInteractions(
                missionClient,
                heartbeatScheduler,
                waypointSimulationService,
                missionResultSimulationService
        );
    }

    @Test
    void shouldKeepResultsPendingUntilProcessingCompletes() {
        stubSuccessfulAck();
        stubSuccessfulResults();

        assertDoesNotThrow(
                () -> missionExecutionService.prepareMission(payload)
        );

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_PENDING,
                executionTracker.getStatus(executionId)
        );

        verify(missionResultSimulationService, times(1))
                .simulateResults(
                        missionId,
                        executionId,
                        List.of(nodeId)
                );

        verify(heartbeatScheduler, times(1))
                .stop(executionId);
    }


    @Test
    void shouldMarkResultsRetryWhenSendingResultsFails() {
        stubSuccessfulAck();

        when(missionResultSimulationService.simulateResults(
                missionId,
                executionId,
                List.of(nodeId)
        )).thenThrow(
                new IllegalStateException(
                        "Simulated Results failure"
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> missionExecutionService.prepareMission(payload)
        );

        assertEquals(
                "Simulated Results failure",
                exception.getMessage()
        );

        assertEquals(
                MissionExecutionTracker.Status.RESULTS_RETRY,
                executionTracker.getStatus(executionId)
        );

        verify(waypointSimulationService, times(1))
                .simulate(
                        missionId,
                        executionId,
                        mission.waypoints()
                );

        verify(heartbeatScheduler, times(1))
                .stop(executionId);
    }

}
