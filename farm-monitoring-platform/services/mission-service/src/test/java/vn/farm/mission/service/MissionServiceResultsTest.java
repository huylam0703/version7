package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
import vn.farm.core.BusinessException;
import vn.farm.core.FarmEvents;
import vn.farm.mission.dto.request.MissionNodeResultRequest;
import vn.farm.mission.dto.request.MissionResultRequest;
import vn.farm.mission.dto.response.MissionResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.mapper.MissionMapper;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.*;
import vn.farm.mission.validation.MissionReferenceValidator;
import vn.farm.starter.security.CurrentUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceResultsTest {

    @Mock
    MissionRepository missionRepository;

    @Mock
    WaypointRepository waypointRepository;

    @Mock
    MissionNodeRepository missionNodeRepository;

    @Mock
    MissionStatusLogRepository missionStatusLogRepository;

    @Mock
    MissionMapper missionMapper;

    @Mock
    CurrentUser currentUser;

    @Mock
    MissionReferenceValidator references;

    @Mock
    MissionExecutionRepository missionExecutionRepository;

    @Mock
    MissionOutboxService missionOutboxService;

    @Mock
    MissionExecutionLockService executionLockService;

    @Mock
    MissionRecoveryOwnershipValidator ownershipValidator;

    @InjectMocks
    MissionService missionService;

    UUID missionId;
    UUID executionId;
    UUID nodeId1;
    UUID nodeId2;

    Mission mission;
    MissionNode node1;
    MissionNode node2;
    MissionExecution execution;

    @BeforeEach
    void setUp() {

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        nodeId1 = UUID.randomUUID();
        nodeId2 = UUID.randomUUID();

        mission = new Mission();
        mission.setMissionId(missionId);
        mission.setStatus(MissionStatus.RUNNING);
        mission.setTotalNodes(2);
        mission.setCreatedBy(UUID.randomUUID());

        node1 = node(nodeId1, "PENDING", 0);
        node2 = node(nodeId2, "PENDING", 0);

        execution = new MissionExecution();
        execution.setExecutionId(executionId);
        execution.setMission(mission);
        execution.setStatus(MissionExecutionStatus.EXECUTING);
        execution.setAssignedClientId("uav-simulator-01");
    }

    private MissionNode node(
            UUID id,
            String status,
            int records
    ) {

        MissionNode node = new MissionNode();

        node.setNodeId(id);
        node.setMission(mission);
        node.setResult(status);
        node.setRecordsCount(records);
        node.setAttempts(0);

        return node;
    }

    private MissionNodeResultRequest result(
            UUID id,
            NodeResultStatus status,
            int records
    ) {

        return MissionNodeResultRequest.builder()
                .nodeId(id)
                .result(status)
                .recordsCount(records)
                .attempts(1)
                .build();
    }

    private MissionResultRequest request(
            MissionNodeResultRequest... results
    ) {

        return MissionResultRequest.builder()
                .executionId(executionId)
                .completed(true)
                .results(List.of(results))
                .build();
    }

    private void stubRunningMission() {

        when(missionRepository.findForResults(missionId))
                .thenReturn(Optional.of(mission));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node1, node2));
    }

    private void stubSuccessfulCompletion() {

        stubRunningMission();

        when(missionExecutionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        when(missionRepository.save(any(Mission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(missionMapper.toMissionResponse(any(Mission.class)))
                .thenReturn(new MissionResponse());

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of());
    }

    private void verifyCompletion(
            MissionStatus expectedMissionStatus,
            MissionExecutionStatus expectedExecutionStatus,
            int expectedSuccess,
            int expectedFailed
    ) {

        assertEquals(
                expectedMissionStatus,
                mission.getStatus()
        );

        assertEquals(
                expectedExecutionStatus,
                execution.getStatus()
        );

        assertEquals(
                expectedSuccess,
                mission.getSuccessNodes()
        );

        assertEquals(
                expectedFailed,
                mission.getFailedNodes()
        );

        assertNotNull(mission.getEndedAt());

        verify(missionNodeRepository)
                .saveAll(anyList());

        verify(missionExecutionRepository)
                .save(execution);

        verify(missionOutboxService, times(1))
                .saveEvent(
                        eq(FarmEvents.MISSION_STATUS_CHANGED),
                        any(MissionStatusChangedPayload.class)
                );

        verify(missionStatusLogRepository, times(1))
                .save(any());
    }

    // TEST 1: All Nodes SUCCESS

    @Test
    void allNodesSuccess_shouldCompleteMission() {

        stubSuccessfulCompletion();

        MissionResponse response =
                missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        10
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        15
                                )
                        )
                );

        assertNotNull(response);

        assertEquals("SUCCESS", node1.getResult());
        assertEquals("SUCCESS", node2.getResult());

        assertEquals(10, node1.getRecordsCount());
        assertEquals(15, node2.getRecordsCount());

        verifyCompletion(
                MissionStatus.COMPLETED,
                MissionExecutionStatus.COMPLETED,
                2,
                0
        );
    }

    // TEST 2: Mixed SUCCESS and FAILED

    @Test
    void mixedResults_shouldMarkMissionPartial() {

        stubSuccessfulCompletion();

        MissionResponse response =
                missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        10
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.FAILED,
                                        0
                                )
                        )
                );

        assertNotNull(response);

        assertEquals("SUCCESS", node1.getResult());
        assertEquals("FAILED", node2.getResult());

        verifyCompletion(
                MissionStatus.PARTIAL,
                MissionExecutionStatus.COMPLETED,
                1,
                1
        );
    }

    // TEST 3: All Nodes FAILED

    @Test
    void allNodesFailed_shouldFailMissionAndExecution() {

        stubSuccessfulCompletion();

        MissionResponse response =
                missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.FAILED,
                                        0
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.FAILED,
                                        0
                                )
                        )
                );

        assertNotNull(response);

        assertEquals("FAILED", node1.getResult());
        assertEquals("FAILED", node2.getResult());

        verifyCompletion(
                MissionStatus.FAILED,
                MissionExecutionStatus.FAILED,
                0,
                2
        );
    }

    // TEST 4: Unknown Node

    @Test
    void unknownNode_shouldRejectWithoutSavingOrPublishing() {

        stubRunningMission();

        assertThrows(
                BusinessException.class,
                () -> missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        UUID.randomUUID(),
                                        NodeResultStatus.SUCCESS,
                                        1
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        1
                                )
                        )
                )
        );

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verifyNoInteractions(missionOutboxService);
    }

    // TEST 5: Duplicate Node

    @Test
    void duplicateNode_shouldRejectWithoutSavingOrPublishing() {

        stubRunningMission();

        assertThrows(
                BusinessException.class,
                () -> missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        1
                                ),
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        1
                                )
                        )
                )
        );

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verifyNoInteractions(missionOutboxService);
    }

    // TEST 6: Mission Not Running

    @Test
    void missionNotRunning_shouldReject() {

        mission.setStatus(MissionStatus.PLANNED);

        when(missionRepository.findForResults(missionId))
                .thenReturn(Optional.of(mission));

        assertThrows(
                BusinessException.class,
                () -> missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        1
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        1
                                )
                        )
                )
        );

        verifyNoInteractions(
                missionNodeRepository,
                missionOutboxService
        );
    }

    // TEST 7: Progress Result Conflict

    @Test
    void progressStatusConflict_shouldReject() {

        node1.setResult("SUCCESS");
        node1.setRecordsCount(5);

        stubRunningMission();

        assertThrows(
                BusinessException.class,
                () -> missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.FAILED,
                                        0
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        1
                                )
                        )
                )
        );

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verifyNoInteractions(missionOutboxService);
    }

    // TEST 8: Identical Retry - Idempotency

    @Test
    void identicalRetry_shouldReturnWithoutNewEvent() {

        mission.setStatus(MissionStatus.COMPLETED);
        mission.setSuccessNodes(2);
        mission.setFailedNodes(0);

        execution.setStatus(MissionExecutionStatus.COMPLETED);

        node1.setResult("SUCCESS");
        node1.setRecordsCount(10);
        node1.setAttempts(1);

        node2.setResult("SUCCESS");
        node2.setRecordsCount(15);
        node2.setAttempts(1);

        when(missionRepository.findForResults(missionId))
                .thenReturn(Optional.of(mission));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node1, node2));

        when(missionExecutionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        when(missionMapper.toMissionResponse(mission))
                .thenReturn(new MissionResponse());

        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of());

        MissionResponse response =
                missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        10
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        15
                                )
                        )
                );

        assertNotNull(response);

        assertEquals(
                executionId,
                response.getExecutionId()
        );

        assertEquals(2, response.getSuccessNodes());
        assertEquals(0, response.getFailedNodes());

        verify(missionRepository, never())
                .save(any());

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verifyNoInteractions(
                missionOutboxService,
                missionStatusLogRepository
        );
    }

    // TEST 9: Conflicting Retry

    @Test
    void conflictingRetry_shouldRejectWithoutNewEvent() {

        mission.setStatus(MissionStatus.COMPLETED);

        execution.setStatus(MissionExecutionStatus.COMPLETED);

        node1.setResult("SUCCESS");
        node1.setRecordsCount(10);
        node1.setAttempts(1);

        node2.setResult("SUCCESS");
        node2.setRecordsCount(15);
        node2.setAttempts(1);

        when(missionRepository.findForResults(missionId))
                .thenReturn(Optional.of(mission));

        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of(node1, node2));

        assertThrows(
                BusinessException.class,
                () -> missionService.receiveMissionResults(
                        missionId,
                        request(
                                result(
                                        nodeId1,
                                        NodeResultStatus.SUCCESS,
                                        99
                                ),
                                result(
                                        nodeId2,
                                        NodeResultStatus.SUCCESS,
                                        15
                                )
                        )
                )
        );

        verify(missionRepository, never())
                .save(any());

        verify(missionNodeRepository, never())
                .saveAll(anyList());

        verifyNoInteractions(
                missionOutboxService,
                missionStatusLogRepository
        );
    }
}