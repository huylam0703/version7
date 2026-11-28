
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.request.MissionNodeProgressRequest;
import uav_gateway_simulator.dto.request.MissionProgressRequest;
import uav_gateway_simulator.dto.response.MissionProgressResponse;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.telemetry.FlightMovementService;
import uav_gateway_simulator.telemetry.FlightOwnershipService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaypointSimulationServiceTest {

    private MissionClient missionClient;
    private WaypointSimulationService simulationService;

    private UUID missionId;
    private UUID executionId;
    private MissionCancellationService cancellationService;
    private WaypointSensorCollectionService sensorCollectionService;
    private FlightMovementService flightMovementService;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);

        RecoveryLeaseRegistry leaseRegistry =
                new RecoveryLeaseRegistry();

        cancellationService =
                mock(MissionCancellationService.class);

        sensorCollectionService =
                mock(WaypointSensorCollectionService.class);
        flightMovementService =
                mock(FlightMovementService.class);
        FlightOwnershipService flightOwnershipService =
                new FlightOwnershipService(leaseRegistry);

        simulationService = new WaypointSimulationService(
                missionClient,
                leaseRegistry,
                cancellationService,
                sensorCollectionService,
                flightMovementService,
                flightOwnershipService
        );

        ReflectionTestUtils.setField(
                simulationService,
                "waypointDelayMs",
                1L
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        when(sensorCollectionService.collect(
                eq(missionId),
                eq(executionId),
                any(WaypointResponse.class)
        )).thenAnswer(invocation -> {
            WaypointResponse waypoint = invocation.getArgument(2);

            return waypoint.nodeIds().stream()
                    .map(id -> new MissionNodeProgressRequest(
                            id,
                            "SUCCESS",
                            1,
                            1,
                            Instant.now()
                    ))
                    .toList();
        });
    }

    private WaypointResponse waypoint(int seq) {
        UUID nodeId = UUID.randomUUID();

        return new WaypointResponse(
                UUID.randomUUID(),
                seq,
                new BigDecimal("10.123"),
                new BigDecimal("106.123"),
                new BigDecimal("20"),
                "PENDING",
                List.of(nodeId)
        );
    }

    private void mockProgressConfirmation() {
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
    }

    @Test
    void shouldSendProgressInWaypointOrder() {
        List<WaypointResponse> waypoints = List.of(
                waypoint(3),
                waypoint(1),
                waypoint(2)
        );

        mockProgressConfirmation();

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

            assertEquals(1, request.nodeResults().size());
            assertEquals(
                    "SUCCESS",
                    request.nodeResults().get(0).result()
            );
            assertEquals(
                    1,
                    request.nodeResults().get(0).recordsCount()
            );
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

    @Test
    void shouldStopWaypointsWhenMissionIsCancelled() {
        List<WaypointResponse> waypoints = List.of(
                waypoint(1),
                waypoint(2),
                waypoint(3)
        );

        AtomicBoolean cancelled = new AtomicBoolean(false);

        doAnswer(invocation -> {
            if (cancelled.get()) {
                throw new MissionCancelledException(
                        "Mission cancelled"
                );
            }
            return null;
        }).when(cancellationService)
                .checkNotCancelled(missionId);

        when(missionClient.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenAnswer(invocation -> {
            MissionProgressRequest request =
                    invocation.getArgument(1);

            cancelled.set(true);

            return new MissionProgressResponse(
                    missionId,
                    executionId,
                    "EXECUTING",
                    request.sequence(),
                    request.waypointSeq(),
                    Instant.now()
            );
        });

        assertThrows(
                MissionCancelledException.class,
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

        verify(cancellationService, atLeastOnce())
                .checkNotCancelled(missionId);
    }
}
