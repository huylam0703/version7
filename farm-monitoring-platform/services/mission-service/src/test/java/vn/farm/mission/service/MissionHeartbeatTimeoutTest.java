
package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.farm.core.FarmEvents;
import vn.farm.mission.entity.*;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionHeartbeatTimeoutTest {

    @Mock MissionRepository missionRepository;
    @Mock MissionExecutionRepository missionExecutionRepository;
    @Mock MissionExecutionLockService executionLockService;
    @Mock MissionStatusLogRepository missionStatusLogRepository;
    @Mock MissionOutboxService missionOutboxService;

    @InjectMocks MissionService missionService;

    UUID missionId;
    UUID executionId;
    Mission mission;
    MissionExecution execution;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        mission = new Mission();
        mission.setMissionId(missionId);
        mission.setStatus(MissionStatus.RUNNING);
        mission.setCreatedBy(UUID.randomUUID());
        mission.setTotalNodes(1);

        execution = new MissionExecution();
        execution.setExecutionId(executionId);
        execution.setMission(mission);
        execution.setStatus(MissionExecutionStatus.EXECUTING);
        execution.setAcknowledgedAt(
                Instant.now().minusSeconds(200)
        );
    }

    private void mockLockedExecution() {
        when(missionExecutionRepository.findById(executionId))
                .thenReturn(Optional.of(execution));

        when(executionLockService.lock(missionId, executionId))
                .thenReturn(execution);
    }

    @Test
    void heartbeatTimeout_shouldFailMissionAndExecution() {
        execution.setLastHeartbeatAt(
                Instant.now().minusSeconds(200)
        );
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.FAILED, mission.getStatus());
        assertEquals(
                MissionExecutionStatus.FAILED,
                execution.getStatus()
        );
        assertNotNull(mission.getEndedAt());

        verify(missionStatusLogRepository).save(any());
        verify(missionOutboxService).saveEvent(
                eq(FarmEvents.MISSION_STATUS_CHANGED),
                any()
        );
    }

    @Test
    void recentHeartbeat_shouldNotFail() {
        execution.setLastHeartbeatAt(
                Instant.now().minusSeconds(10)
        );
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.RUNNING, mission.getStatus());
        assertEquals(
                MissionExecutionStatus.EXECUTING,
                execution.getStatus()
        );

        verify(missionStatusLogRepository, never()).save(any());
        verifyNoInteractions(missionOutboxService);
    }

    @Test
    void withinRecoveryWindow_shouldNotFail() {
        execution.setLastHeartbeatAt(
                Instant.now().minusSeconds(100)
        );
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.RUNNING, mission.getStatus());
        verifyNoInteractions(missionOutboxService);
    }

    @Test
    void completedMission_shouldNotChange() {
        mission.setStatus(MissionStatus.COMPLETED);
        execution.setStatus(MissionExecutionStatus.COMPLETED);
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.COMPLETED, mission.getStatus());
        verifyNoInteractions(missionOutboxService);
    }

    @Test
    void cancelledMission_shouldNotChange() {
        mission.setStatus(MissionStatus.CANCELLED);
        execution.setStatus(MissionExecutionStatus.CANCELLED);
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.CANCELLED, mission.getStatus());
        verifyNoInteractions(missionOutboxService);
    }

    @Test
    void alreadyFailed_shouldNotPublishAgain() {
        mission.setStatus(MissionStatus.FAILED);
        execution.setStatus(MissionExecutionStatus.FAILED);
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        verifyNoInteractions(missionOutboxService);
        verify(missionStatusLogRepository, never()).save(any());
    }

    @Test
    void recoveredHeartbeat_shouldPreventTimeout() {
        execution.setLastHeartbeatAt(
                Instant.now().minusSeconds(5)
        );
        execution.setRecoveryOwnerId("uav-simulator-01");
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.RUNNING, mission.getStatus());
        verifyNoInteractions(missionOutboxService);
    }

    @Test
    void noHeartbeat_shouldUseAcknowledgedAt() {
        execution.setLastHeartbeatAt(null);
        execution.setAcknowledgedAt(
                Instant.now().minusSeconds(200)
        );
        mockLockedExecution();

        missionService.failMissionOnHeartbeatTimeout(executionId);

        assertEquals(MissionStatus.FAILED, mission.getStatus());
        assertEquals(
                MissionExecutionStatus.FAILED,
                execution.getStatus()
        );
    }
}
