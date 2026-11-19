
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionProgressRequest;
import uav_gateway_simulator.dto.MissionProgressResponse;
import uav_gateway_simulator.dto.MissionRecoveryClaimResponse;
import uav_gateway_simulator.dto.MissionResumePlan;
import uav_gateway_simulator.dto.WaypointResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaypointSimulationResumeTest {

    private MissionClient missionClient;
    private RecoveryLeaseRegistry leaseRegistry;
    private WaypointSimulationService service;

    private UUID missionId;
    private UUID executionId;
    private UUID instanceId;

    private List<WaypointResponse> waypoints;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);
        leaseRegistry = new RecoveryLeaseRegistry();

        service = new WaypointSimulationService(
                missionClient,
                leaseRegistry
        );

        ReflectionTestUtils.setField(
                service,
                "waypointDelayMs",
                0L
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        instanceId = UUID.randomUUID();

        waypoints = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            waypoints.add(new WaypointResponse(
                    UUID.randomUUID(),
                    i,
                    BigDecimal.valueOf(10.762622),
                    BigDecimal.valueOf(106.660172),
                    BigDecimal.valueOf(15.5),
                    "PENDING"
            ));
        }
    }

    private void registerValidLease() {
        leaseRegistry.beginRecovery(executionId);

        leaseRegistry.register(
                new MissionRecoveryClaimResponse(
                        missionId,
                        executionId,
                        instanceId,
                        1L,
                        Instant.now().plusSeconds(120)
                )
        );
    }

    private MissionResumePlan resumeFrom(int seq) {
        return new MissionResumePlan(
                missionId,
                executionId,
                seq,
                (short) seq,
                false
        );
    }

    private MissionProgressResponse progressResponse(
            long progressSequence,
            int waypointSeq
    ) {
        MissionProgressResponse response =
                mock(MissionProgressResponse.class);

        when(response.missionId()).thenReturn(missionId);
        when(response.executionId()).thenReturn(executionId);
        when(response.lastConfirmedSequence())
                .thenReturn(progressSequence);
        when(response.waypointSeq())
                .thenReturn((short) waypointSeq);

        return response;
    }

    @Test
    void shouldResumeOnlyRemainingWaypoints() {
        registerValidLease();

        MissionProgressResponse response3 = progressResponse(3, 3);
        MissionProgressResponse response4 = progressResponse(4, 4);
        MissionProgressResponse response5 = progressResponse(5, 5);

        when(missionClient.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenReturn(
                response3,
                response4,
                response5
        );

        service.simulateResume(
                missionId,
                executionId,
                waypoints,
                resumeFrom(3)
        );

        var captor = org.mockito.ArgumentCaptor.forClass(
                MissionProgressRequest.class
        );

        verify(missionClient, times(3))
                .updateProgress(eq(missionId), captor.capture());

        List<MissionProgressRequest> requests =
                captor.getAllValues();

        assertEquals(3, requests.size());

        assertEquals((short) 3, requests.get(0).waypointSeq());
        assertEquals((short) 4, requests.get(1).waypointSeq());
        assertEquals((short) 5, requests.get(2).waypointSeq());

        assertEquals(3L, requests.get(0).sequence());
        assertEquals(4L, requests.get(1).sequence());
        assertEquals(5L, requests.get(2).sequence());
    }

    @Test
    void shouldSkipWhenAllWaypointsReached() {
        registerValidLease();

        MissionResumePlan plan = new MissionResumePlan(
                missionId,
                executionId,
                6L,
                null,
                true
        );

        service.simulateResume(
                missionId,
                executionId,
                waypoints,
                plan
        );

        verify(missionClient, never())
                .updateProgress(any(), any());
    }

    @Test
    void shouldRejectResumeWithoutLease() {
        leaseRegistry.beginRecovery(executionId);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.simulateResume(
                        missionId,
                        executionId,
                        waypoints,
                        resumeFrom(3)
                )
        );

        assertTrue(
                exception.getMessage().contains(
                        "Recovery claim required"
                )
        );

        verify(missionClient, never())
                .updateProgress(any(), any());
    }

    @Test
    void shouldStopWhenOwnershipLost() {
        registerValidLease();

        when(missionClient.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenAnswer(invocation -> {
            leaseRegistry.markLost(executionId);
            return progressResponse(3, 3);
        });

        assertThrows(
                IllegalStateException.class,
                () -> service.simulateResume(
                        missionId,
                        executionId,
                        waypoints,
                        resumeFrom(3)
                )
        );

        verify(missionClient, times(1))
                .updateProgress(
                        eq(missionId),
                        any(MissionProgressRequest.class)
                );
    }

    @Test
    void shouldRejectInvalidProgressConfirmation() {
        registerValidLease();

        MissionProgressResponse invalidResponse =
                progressResponse(99, 3);

        when(missionClient.updateProgress(
                eq(missionId),
                any(MissionProgressRequest.class)
        )).thenReturn(invalidResponse);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.simulateResume(
                        missionId,
                        executionId,
                        waypoints,
                        resumeFrom(3)
                )
        );

        assertEquals(
                "Invalid resume progress confirmation",
                exception.getMessage()
        );

        verify(missionClient, times(1))
                .updateProgress(
                        eq(missionId),
                        any(MissionProgressRequest.class)
                );
    }
}
