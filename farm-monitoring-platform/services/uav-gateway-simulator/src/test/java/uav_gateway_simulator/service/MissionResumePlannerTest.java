
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionExecutionProgressResponse;
import uav_gateway_simulator.dto.response.MissionProgressDetailResponse;
import uav_gateway_simulator.dto.MissionResumePlan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionResumePlannerTest {

    private MissionClient missionClient;
    private MissionResumePlanner planner;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);
        planner = new MissionResumePlanner(missionClient);

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    private void mockProgress(int reachedCount) {
        when(missionClient.getExecutionProgress(
                missionId, executionId
        )).thenReturn(new MissionExecutionProgressResponse(
                missionId,
                executionId,
                "EXECUTING",
                reachedCount,
                Instant.now(),
                Instant.now()
        ));

        List<MissionProgressDetailResponse.WaypointProgress>
                waypoints = java.util.stream.IntStream
                .rangeClosed(1, 5)
                .mapToObj(seq ->
                        new MissionProgressDetailResponse.WaypointProgress(
                                UUID.randomUUID(),
                                (short) seq,
                                seq <= reachedCount
                                        ? "REACHED" : "PENDING",
                                seq <= reachedCount
                                        ? Instant.now() : null
                        )
                )
                .toList();

        when(missionClient.getMissionProgress(missionId))
                .thenReturn(new MissionProgressDetailResponse(
                        missionId,
                        executionId,
                        "RUNNING",
                        "EXECUTING",
                        reachedCount,
                        5,
                        reachedCount,
                        1,
                        0,
                        reachedCount * 20.0,
                        waypoints,
                        List.of()
                ));
    }

    @Test
    void shouldResumeFromWaypoint3() {
        mockProgress(2);

        MissionResumePlan plan =
                planner.createPlan(missionId, executionId);

        assertEquals(3L, plan.nextProgressSequence());
        assertEquals(Short.valueOf((short) 3),
                plan.nextWaypointSeq());
        assertFalse(plan.allWaypointsReached());
    }

    @Test
    void shouldResumeFromFirstWaypoint() {
        mockProgress(0);

        MissionResumePlan plan =
                planner.createPlan(missionId, executionId);

        assertEquals(1L, plan.nextProgressSequence());
        assertEquals(Short.valueOf((short) 1),
                plan.nextWaypointSeq());
        assertFalse(plan.allWaypointsReached());
    }

    @Test
    void shouldRecognizeAllWaypointsReached() {
        mockProgress(5);

        MissionResumePlan plan =
                planner.createPlan(missionId, executionId);

        assertEquals(6L, plan.nextProgressSequence());
        assertNull(plan.nextWaypointSeq());
        assertTrue(plan.allWaypointsReached());
    }

    @Test
    void shouldRejectInconsistentProgress() {
        mockProgress(2);

        when(missionClient.getExecutionProgress(
                missionId, executionId
        )).thenReturn(new MissionExecutionProgressResponse(
                missionId,
                executionId,
                "EXECUTING",
                3L,
                Instant.now(),
                Instant.now()
        ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.createPlan(missionId, executionId)
        );

        assertEquals(
                "Inconsistent execution progress",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectDuplicateWaypointSequence() {
        mockProgress(2);

        var waypoints = List.of(
                new MissionProgressDetailResponse.WaypointProgress(
                        UUID.randomUUID(), (short) 1,
                        "REACHED", Instant.now()
                ),
                new MissionProgressDetailResponse.WaypointProgress(
                        UUID.randomUUID(), (short) 1,
                        "REACHED", Instant.now()
                )
        );

        when(missionClient.getMissionProgress(missionId))
                .thenReturn(new MissionProgressDetailResponse(
                        missionId, executionId,
                        "RUNNING", "EXECUTING",
                        2L, 2, 2, 1, 0, 100.0,
                        waypoints, List.of()
                ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.createPlan(missionId, executionId)
        );

        assertEquals(
                "Duplicate waypoint sequence",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNonContiguousReachedWaypoints() {
        mockProgress(2);

        var waypoints = List.of(
                new MissionProgressDetailResponse.WaypointProgress(
                        UUID.randomUUID(), (short) 1,
                        "REACHED", Instant.now()
                ),
                new MissionProgressDetailResponse.WaypointProgress(
                        UUID.randomUUID(), (short) 2,
                        "PENDING", null
                ),
                new MissionProgressDetailResponse.WaypointProgress(
                        UUID.randomUUID(), (short) 3,
                        "REACHED", Instant.now()
                )
        );

        when(missionClient.getMissionProgress(missionId))
                .thenReturn(new MissionProgressDetailResponse(
                        missionId, executionId,
                        "RUNNING", "EXECUTING",
                        2L, 3, 2, 1, 0, 66.67,
                        waypoints, List.of()
                ));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> planner.createPlan(missionId, executionId)
        );

        assertEquals(
                "Non-contiguous reached waypoints",
                exception.getMessage()
        );
    }
}
