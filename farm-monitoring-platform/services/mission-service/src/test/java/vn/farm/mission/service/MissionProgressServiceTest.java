package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionNodeProgressRequest;
import vn.farm.mission.dto.request.MissionProgressRequest;
import vn.farm.mission.dto.response.MissionProgressResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionProgressServiceTest {

    @Mock
    private MissionExecutionRepository executionRepository;

    @Mock
    private WaypointRepository waypointRepository;

    @Mock
    private MissionNodeRepository missionNodeRepository;

    @Mock
    private MissionExecutionLockService executionLockService;

    @Mock
    private MissionRecoveryOwnershipValidator ownershipValidator;

    @InjectMocks
    private MissionProgressService progressService;

    private UUID missionId;
    private UUID executionId;
    private UUID nodeId;

    private Mission mission;
    private MissionExecution execution;
    private Waypoint waypoint;
    private MissionNode node;
    private MissionProgressRequest request;

    @BeforeEach
    void setUp() {

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        nodeId = UUID.randomUUID();

        mission = Mission.builder()
                .missionId(missionId)
                .status(MissionStatus.RUNNING)
                .build();

        execution = MissionExecution.builder()
                .executionId(executionId)
                .mission(mission)
                .status(MissionExecutionStatus.EXECUTING)
                .lastConfirmedSequence(0L)
                .build();

        waypoint = Waypoint.builder()
                .waypointId(UUID.randomUUID())
                .mission(mission)
                .seq((short) 1)
                .status("PENDING")
                .build();

        node = MissionNode.builder()
                .missionNodeId(UUID.randomUUID())
                .mission(mission)
                .waypoint(waypoint)
                .nodeId(nodeId)
                .result("PENDING")
                .build();

        MissionNodeProgressRequest nodeRequest =
                MissionNodeProgressRequest.builder()
                        .nodeId(nodeId)
                        .result(NodeResultStatus.SUCCESS)
                        .recordsCount(5)
                        .attempts(1)
                        .collectedAt(Instant.now())
                        .build();

        request = MissionProgressRequest.builder()
                .executionId(executionId)
                .sequence(1)
                .waypointSeq((short) 1)
                .reachedAt(Instant.now())
                .nodeResults(List.of(nodeRequest))
                .build();
    }

    private void mockExecution() {
        when(executionLockService.lock(
                missionId, executionId
        )).thenReturn(execution);
    }

    private void mockWaypoint() {
        when(waypointRepository.findByMission_MissionIdAndSeq(
                missionId, (short) 1
        )).thenReturn(Optional.of(waypoint));
    }

    private void mockNode() {
        when(missionNodeRepository.findByMission_MissionIdAndNodeId(
                missionId, nodeId
        )).thenReturn(Optional.of(node));
    }

    @Test
    void TC01_updateProgress_success() {

        mockExecution();
        mockWaypoint();
        mockNode();

        MissionProgressResponse response =
                progressService.updateProgress(missionId, request);

        assertNotNull(response);
        assertEquals(missionId, response.getMissionId());
        assertEquals(executionId, response.getExecutionId());
        assertEquals(1L, response.getLastConfirmedSequence());

        assertEquals("REACHED", waypoint.getStatus());
        assertEquals("SUCCESS", node.getResult());
        assertEquals(5, node.getRecordsCount());
        assertEquals(1, execution.getLastConfirmedSequence());

        verify(waypointRepository).save(waypoint);
        verify(missionNodeRepository).saveAll(List.of(node));
        verify(executionRepository).save(execution);
    }

    @Test
    void TC02_executionNotFound() {

        when(executionLockService.lock(
                missionId, executionId
        )).thenThrow(BusinessException.notFound(
                "EXECUTION_NOT_FOUND",
                "Không tìm thấy Mission Execution"
        ));

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC03_missionNotRunning() {

        mission.setStatus(MissionStatus.FAILED);
        mockExecution();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC04_executionNotExecuting() {

        execution.setStatus(MissionExecutionStatus.WAITING_ACK);
        mockExecution();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC05_duplicateSequence() {

        execution.setLastConfirmedSequence(1L);
        mockExecution();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC06_outOfOrderSequence() {

        request.setSequence(3L);
        mockExecution();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC07_waypointNotFound() {

        mockExecution();

        when(waypointRepository.findByMission_MissionIdAndSeq(
                missionId, (short) 1
        )).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(missionNodeRepository);
    }

    @Test
    void TC08_duplicateNodeInRequest() {

        MissionNodeProgressRequest item =
                request.getNodeResults().get(0);

        request.setNodeResults(List.of(item, item));

        mockExecution();
        mockWaypoint();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        verifyNoInteractions(missionNodeRepository);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void TC09_getProgress_executionNotFound() {

        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> progressService.getProgress(missionId)
        );
    }

    @Test
    void TC10_getProgress_calculatePercentage() {

        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        Waypoint w2 = Waypoint.builder()
                .seq((short) 2)
                .status("REACHED")
                .build();

        Waypoint w3 = Waypoint.builder()
                .seq((short) 3)
                .status("PENDING")
                .build();

        waypoint.setStatus("REACHED");

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of(waypoint, w2, w3));

        node.setResult("SUCCESS");

        MissionNode n2 = MissionNode.builder()
                .nodeId(UUID.randomUUID())
                .result("PENDING")
                .build();

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node, n2));

        var response = progressService.getProgress(missionId);

        assertEquals(3, response.getTotalWaypoints());
        assertEquals(2, response.getReachedWaypoints());
        assertEquals(2, response.getTotalNodes());
        assertEquals(1, response.getProcessedNodes());
        assertEquals(58.33, response.getProgressPercent(), 0.01);
    }

    @Test
    void TC11_getProgress_returnDetails() {

        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of(waypoint));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node));

        var response = progressService.getProgress(missionId);

        assertEquals(missionId, response.getMissionId());
        assertEquals(executionId, response.getExecutionId());
        assertEquals("RUNNING", response.getMissionStatus());
        assertEquals("EXECUTING", response.getExecutionStatus());

        assertEquals(1, response.getWaypoints().size());
        assertEquals(1, response.getNodes().size());

        assertEquals(waypoint.getWaypointId(),
                response.getWaypoints().get(0).getWaypointId());

        assertEquals(nodeId,
                response.getNodes().get(0).getNodeId());
    }

    @Test
    void TC12_nodeNotFound() {
        mockExecution();
        mockWaypoint();

        when(missionNodeRepository.findByMission_MissionIdAndNodeId(
                missionId, nodeId
        )).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        assertEquals(0L, execution.getLastConfirmedSequence());
        assertEquals("PENDING", waypoint.getStatus());

        verify(executionRepository, never()).save(any());
        verify(waypointRepository, never()).save(any());
    }

    @Test
    void TC13_waypointAlreadyReached() {
        waypoint.setStatus("REACHED");

        mockExecution();
        mockWaypoint();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        assertEquals(0L, execution.getLastConfirmedSequence());

        verifyNoInteractions(missionNodeRepository);
    }

    @Test
    void TC14_progressWithoutNodes() {
        request.setNodeResults(List.of());

        mockExecution();
        mockWaypoint();

        MissionProgressResponse response =
                progressService.updateProgress(missionId, request);

        assertNotNull(response);
        assertEquals(1L, response.getLastConfirmedSequence());
        assertEquals("REACHED", waypoint.getStatus());

        verifyNoInteractions(missionNodeRepository);
        verify(waypointRepository).save(waypoint);
        verify(executionRepository).save(execution);
    }

    @Test
    void TC15_invalidSequenceDoesNotUpdateExecution() {
        execution.setLastConfirmedSequence(2L);
        request.setSequence(5L);

        mockExecution();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(missionId, request)
        );

        assertEquals(2L, execution.getLastConfirmedSequence());

        verify(executionRepository, never()).save(any());
        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC16_getProgress_noProgress() {
        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of(waypoint));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node));

        var response = progressService.getProgress(missionId);

        assertEquals(0, response.getReachedWaypoints());
        assertEquals(0, response.getProcessedNodes());
        assertEquals(0.0, response.getProgressPercent(), 0.01);
    }


    @Test
    void TC17_getProgress_onlyWaypoints() {

        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        waypoint.setStatus("REACHED");

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of(waypoint));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of());

        var response = progressService.getProgress(missionId);

        assertEquals(1, response.getTotalWaypoints());
        assertEquals(1, response.getReachedWaypoints());
        assertEquals(0, response.getTotalNodes());
        assertEquals(100.0, response.getProgressPercent(), 0.01);
    }

    @Test
    void TC18_getProgress_onlyNodes() {

        when(executionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of());

        node.setResult("SUCCESS");

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node));

        var response = progressService.getProgress(missionId);

        assertEquals(0, response.getTotalWaypoints());
        assertEquals(1, response.getTotalNodes());
        assertEquals(1, response.getProcessedNodes());
        assertEquals(100.0, response.getProgressPercent(), 0.01);
    }

    @Test
    void TC19_simulatorAssignedClient_success() {
        String clientId = "uav-simulator-01";
        execution.setAssignedClientId(clientId);
        mockExecution();
        mockWaypoint();
        mockNode();

        MissionProgressResponse response = progressService.updateProgress(missionId, request, clientId);

        assertNotNull(response);
        assertEquals(missionId, response.getMissionId());
        assertEquals(executionId, response.getExecutionId());
        assertEquals(1L, response.getLastConfirmedSequence());
        assertEquals("REACHED", waypoint.getStatus());
        assertEquals("SUCCESS", node.getResult());
        verify(waypointRepository).save(waypoint);
        verify(missionNodeRepository).saveAll(List.of(node));
        verify(executionRepository).save(execution);
    }

    @Test
    void TC20_simulatorNotAssigned_rejectedWithoutChanges() {
        execution.setAssignedClientId("uav-simulator-01");
        mockExecution();

        assertThrows(BusinessException.class,
                () -> progressService.updateProgress(missionId, request, "other-simulator"));

        assertEquals(0L, execution.getLastConfirmedSequence());
        assertEquals("PENDING", waypoint.getStatus());
        assertEquals("PENDING", node.getResult());
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(waypointRepository, missionNodeRepository);
    }

    @Test
    void TC21_simulatorClientIdMissing_rejectedBeforeDatabaseAccess() {
        assertThrows(BusinessException.class,
                () -> progressService.updateProgress(missionId, request, (String) null));
        assertThrows(BusinessException.class,
                () -> progressService.updateProgress(missionId, request, ""));
        assertThrows(BusinessException.class,
                () -> progressService.updateProgress(missionId, request, "   "));

    }
    @Test
    void TC22_nodeAssignedToDifferentWaypoint_shouldReject() {

        Waypoint otherWaypoint = Waypoint.builder()
                .waypointId(UUID.randomUUID())
                .mission(mission)
                .seq((short) 2)
                .status("PENDING")
                .build();

        node.setWaypoint(otherWaypoint);

        mockExecution();
        mockWaypoint();
        mockNode();

        assertThrows(
                BusinessException.class,
                () -> progressService.updateProgress(
                        missionId, request
                )
        );

        assertEquals("PENDING", node.getResult());
        assertEquals("PENDING", waypoint.getStatus());
        assertEquals(0L, execution.getLastConfirmedSequence());

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verify(waypointRepository, never())
                .save(any());

        verify(executionRepository, never())
                .save(any());
    }
}
