package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaypointSimulationServiceTest {

    private MissionClient missionClient;
    private WaypointSimulationService simulationService;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);
        RecoveryLeaseRegistry leaseRegistry =
                new RecoveryLeaseRegistry();

        simulationService = new WaypointSimulationService(
                missionClient,
                leaseRegistry
        );

        ReflectionTestUtils.setField(
                simulationService,
                "waypointDelayMs",
                1L
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    private WaypointResponse waypoint(int seq) {
        return new WaypointResponse(
                UUID.randomUUID(),
                seq,
                new BigDecimal("10.123"),
                new BigDecimal("106.123"),
                new BigDecimal("20"),
                "PENDING"
        );
    }

    @Test
    void shouldSendProgressInWaypointOrder() {

        List<WaypointResponse> waypoints = List.of(
                waypoint(3),
                waypoint(1),
                waypoint(2)
        );

        when(missionClient.updateProgress(
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

        simulationService.simulate(
                missionId,
                executionId,
                waypoints
        );

        ArgumentCaptor<MissionProgressRequest> captor =
                ArgumentCaptor.forClass(MissionProgressRequest.class);

        verify(missionClient, times(3))
                .updateProgress(eq(missionId), captor.capture());

        List<MissionProgressRequest> requests =
                captor.getAllValues();

        assertEquals(
                List.of(1L, 2L, 3L),
                requests.stream()
                        .map(MissionProgressRequest::sequence)
                        .toList()
        );

        assertEquals(
                List.of((short) 1, (short) 2, (short) 3),
                requests.stream()
                        .map(MissionProgressRequest::waypointSeq)
                        .toList()
        );

        for (MissionProgressRequest request : requests) {
            assertEquals(executionId, request.executionId());
            assertNotNull(request.reachedAt());
            assertTrue(request.nodeResults().isEmpty());
        }
    }

    @Test
    void shouldRejectEmptyWaypoints() {

        assertThrows(
                IllegalArgumentException.class,
                () -> simulationService.simulate(
                        missionId,
                        executionId,
                        List.of()
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldStopWhenProgressConfirmationIsInvalid() {

        List<WaypointResponse> waypoints = List.of(
                waypoint(1),
                waypoint(2)
        );

        when(missionClient.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenReturn(
                new MissionProgressResponse(
                        missionId,
                        executionId,
                        "EXECUTING",
                        999L,
                        (short) 1,
                        Instant.now()
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> simulationService.simulate(
                        missionId,
                        executionId,
                        waypoints
                )
        );

        verify(missionClient, times(1))
                .updateProgress(
                        eq(missionId),
                        any(MissionProgressRequest.class)
                );
    }
}