package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.farm.core.BusinessException;
import vn.farm.mission.config.SimulatorProperties;
import vn.farm.mission.dto.response.MissionDetailResponse;
import vn.farm.mission.entity.Mission;
import vn.farm.mission.entity.MissionExecution;
import vn.farm.mission.entity.MissionStatus;
import vn.farm.mission.mapper.MissionMapper;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.*;
import vn.farm.mission.validation.MissionReferenceValidator;
import vn.farm.starter.security.CurrentUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private WaypointRepository waypointRepository;
    @Mock private MissionNodeRepository missionNodeRepository;
    @Mock private MissionStatusLogRepository missionStatusLogRepository;
    @Mock private MissionMapper missionMapper;
    @Mock private CurrentUser currentUser;
    @Mock private MissionReferenceValidator references;
    @Mock private MissionExecutionRepository missionExecutionRepository;
    @Mock private MissionOutboxService missionOutboxService;
    @Mock private SimulatorProperties simulatorProperties;

    @InjectMocks private MissionService missionService;

    private UUID missionId;
    private Mission mission;
    private MissionExecution execution;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        mission = Mission.builder()
                .missionId(missionId)
                .status(MissionStatus.RUNNING)
                .build();
        execution = MissionExecution.builder()
                .mission(mission)
                .assignedClientId("uav-simulator-01")
                .build();
    }

    @Test
    void TC01_getMissionById_assignedSimulator_success() {
        when(missionExecutionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));
        when(missionRepository.findById(missionId))
                .thenReturn(Optional.of(mission));
        when(waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId))
                .thenReturn(List.of());
        when(missionNodeRepository.findByMission_MissionId(missionId))
                .thenReturn(List.of());
        MissionDetailResponse expected = MissionDetailResponse.builder()
                .missionId(missionId)
                .build();
        when(missionMapper.toMissionDetailResponse(mission)).thenReturn(expected);

        MissionDetailResponse result = missionService.getMissionById(
                missionId, "uav-simulator-01");

        assertNotNull(result);
        assertEquals(missionId, result.getMissionId());
        assertNotNull(result.getWaypoints());
        assertNotNull(result.getNodeIds());
        verify(missionRepository).findById(missionId);
        verify(missionMapper).toMissionDetailResponse(mission);
    }
    @Test
    void TC02_getMissionById_unassignedSimulator_rejected() {
        when(missionExecutionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.of(execution));

        assertThrows(BusinessException.class,
                () -> missionService.getMissionById(missionId, "other-simulator"));

        verifyNoInteractions(missionRepository, waypointRepository,
                missionNodeRepository, missionMapper);
    }

    @Test
    void TC03_getMissionById_missingClientId_rejected() {
        assertThrows(BusinessException.class,
                () -> missionService.getMissionById(missionId, null));
        assertThrows(BusinessException.class,
                () -> missionService.getMissionById(missionId, ""));
        assertThrows(BusinessException.class,
                () -> missionService.getMissionById(missionId, "   "));

        verifyNoInteractions(missionExecutionRepository, missionRepository,
                waypointRepository, missionNodeRepository, missionMapper);
    }

    @Test
    void TC04_getMissionById_executionNotFound_rejected() {
        when(missionExecutionRepository.findByMission_MissionId(missionId))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> missionService.getMissionById(missionId, "uav-simulator-01"));

        verifyNoInteractions(missionRepository, waypointRepository,
                missionNodeRepository, missionMapper);
    }
}
