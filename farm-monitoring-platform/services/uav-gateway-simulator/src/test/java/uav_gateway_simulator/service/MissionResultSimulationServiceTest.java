
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.dto.response.MissionProgressDetailResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissionResultSimulationServiceTest {

    private MissionClient missionClient;
    private MissionResultSimulationService service;

    private UUID missionId;
    private UUID executionId;
    private UUID nodeId1;
    private UUID nodeId2;

    @BeforeEach
    void setUp() {
        missionClient = mock(MissionClient.class);

        MissionResultPayloadStore payloadStore =
                new MissionResultPayloadStore();

        RecoveryLeaseRegistry leaseRegistry =
                new RecoveryLeaseRegistry();

        MissionCancellationService cancellationService =
                mock(MissionCancellationService.class);

        service = new MissionResultSimulationService(
                missionClient,
                payloadStore,
                leaseRegistry,
                cancellationService
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        nodeId1 = UUID.randomUUID();
        nodeId2 = UUID.randomUUID();
    }

    private void mockCompletedProgress(List<UUID> nodeIds) {
        List<MissionProgressDetailResponse.NodeProgress> nodes =
                nodeIds.stream()
                        .map(id -> new MissionProgressDetailResponse.NodeProgress(
                                id,
                                "SUCCESS",
                                1,
                                1,
                                Instant.now()
                        ))
                        .toList();

        when(missionClient.getMissionProgress(missionId))
                .thenReturn(new MissionProgressDetailResponse(
                        missionId,
                        executionId,
                        "RUNNING",
                        "EXECUTING",
                        1L,
                        1,
                        1,
                        nodes.size(),
                        nodes.size(),
                        100.0,
                        List.of(),
                        nodes
                ));
    }

    @Test
    void shouldGenerateSuccessResultsForAllNodes() {
        mockCompletedProgress(List.of(nodeId1, nodeId2));

        MissionResultResponse expectedResponse =
                new MissionResultResponse(
                        missionId,
                        executionId,
                        "COMPLETED",
                        2,
                        2,
                        0
                );

        when(missionClient.sendResults(
                eq(missionId),
                any(MissionResultRequest.class)
        )).thenReturn(expectedResponse);

        MissionResultResponse response =
                service.simulateResults(
                        missionId,
                        executionId,
                        List.of(nodeId1, nodeId2)
                );

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());

        ArgumentCaptor<MissionResultRequest> captor =
                ArgumentCaptor.forClass(MissionResultRequest.class);

        verify(missionClient, times(1))
                .sendResults(eq(missionId), captor.capture());

        MissionResultRequest request = captor.getValue();

        assertEquals(executionId, request.executionId());
        assertTrue(request.completed());
        assertEquals(2, request.results().size());

        assertEquals(
                List.of(nodeId1, nodeId2),
                request.results().stream()
                        .map(result -> result.nodeId())
                        .toList()
        );

        request.results().forEach(result -> {
            assertEquals("SUCCESS", result.result());
            assertEquals(1, result.recordsCount());
            assertEquals(1, result.attempts());
            assertNotNull(result.collectedAt());
            assertNull(result.errorCode());
            assertNull(result.errorMessage());
        });
    }

    @Test
    void shouldRejectEmptyNodeList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulateResults(
                        missionId,
                        executionId,
                        List.of()
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectNullNodeList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulateResults(
                        missionId,
                        executionId,
                        null
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectDuplicateNodeIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulateResults(
                        missionId,
                        executionId,
                        List.of(nodeId1, nodeId1)
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectNullNodeId() {
        List<UUID> nodeIds = new ArrayList<>();
        nodeIds.add(nodeId1);
        nodeIds.add(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulateResults(
                        missionId,
                        executionId,
                        nodeIds
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectNullExecutionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.simulateResults(
                        missionId,
                        null,
                        List.of(nodeId1)
                )
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldPropagateMissionClientFailure() {
        mockCompletedProgress(List.of(nodeId1));

        when(missionClient.sendResults(
                eq(missionId),
                any(MissionResultRequest.class)
        )).thenThrow(
                new IllegalStateException(
                        "Mission Service unavailable"
                )
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.simulateResults(
                                missionId,
                                executionId,
                                List.of(nodeId1)
                        )
                );

        assertEquals(
                "Mission Service unavailable",
                exception.getMessage()
        );

        verify(missionClient, times(1))
                .sendResults(
                        eq(missionId),
                        any(MissionResultRequest.class)
                );
    }
}
