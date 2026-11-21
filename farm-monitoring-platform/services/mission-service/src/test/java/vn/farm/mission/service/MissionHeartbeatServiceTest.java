package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionHeartbeatRequest;
import vn.farm.mission.dto.response.MissionHeartbeatResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.repository.MissionExecutionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionHeartbeatServiceTest {

    private MissionExecutionRepository executionRepository;
    private MissionHeartbeatService heartbeatService;

    private UUID missionId;
    private UUID executionId;
    private String clientId;

    private Mission mission;
    private MissionExecution execution;
    private MissionHeartbeatRequest request;

    @Mock
    private MissionExecutionRepository missionExecutionRepository;

    @Mock
    private MissionRecoveryOwnershipValidator ownershipValidator;

    @Mock
    private MissionExecutionLockService lockService;

    @BeforeEach
    void setUp() {
        executionRepository = mock(MissionExecutionRepository.class);
        ownershipValidator = mock(MissionRecoveryOwnershipValidator.class);
        lockService = mock(MissionExecutionLockService.class);

        heartbeatService = new MissionHeartbeatService(
                executionRepository,
                ownershipValidator,
                lockService
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
        clientId = "uav-simulator-01";

        mission = mock(Mission.class);
        when(mission.getStatus()).thenReturn(MissionStatus.RUNNING);

        execution = MissionExecution.builder()
                .executionId(executionId)
                .mission(mission)
                .status(MissionExecutionStatus.EXECUTING)
                .assignedClientId(clientId)
                .build();

        request = new MissionHeartbeatRequest(
                missionId,
                executionId,
                1L
        );
    }

    @Test
    void shouldUpdateHeartbeatSuccessfully() {

        when(executionRepository.findForProgress(
                executionId, missionId
        )).thenReturn(Optional.of(execution));

        MissionHeartbeatResponse response =
                heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        clientId
                );

        assertNotNull(response);
        assertEquals(missionId, response.getMissionId());
        assertEquals(executionId, response.getExecutionId());
        assertEquals("EXECUTING", response.getExecutionStatus());

        assertNotNull(response.getLastHeartbeatAt());
        assertEquals(
                execution.getLastHeartbeatAt(),
                response.getLastHeartbeatAt()
        );

        verify(executionRepository, times(1))
                .save(execution);
    }

    @Test
    void shouldRejectInvalidClientId() {

        when(executionRepository.findForProgress(
                executionId, missionId
        )).thenReturn(Optional.of(execution));

        assertThrows(
                BusinessException.class,
                () -> heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        "unknown-client"
                )
        );

        assertNull(execution.getLastHeartbeatAt());

        verify(executionRepository, never())
                .save(any(MissionExecution.class));
    }

    @Test
    void shouldRejectExecutionNotExecuting() {

        execution.setStatus(MissionExecutionStatus.WAITING_ACK);

        when(executionRepository.findForProgress(
                executionId, missionId
        )).thenReturn(Optional.of(execution));

        assertThrows(
                BusinessException.class,
                () -> heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        clientId
                )
        );

        verify(executionRepository, never())
                .save(any(MissionExecution.class));
    }

    @Test
    void shouldRejectMissionNotRunning() {

        when(mission.getStatus()).thenReturn(MissionStatus.COMPLETED);

        when(executionRepository.findForProgress(
                executionId, missionId
        )).thenReturn(Optional.of(execution));

        assertThrows(
                BusinessException.class,
                () -> heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        clientId
                )
        );

        verify(executionRepository, never())
                .save(any(MissionExecution.class));
    }

    @Test
    void shouldRejectExecutionNotFound() {

        when(executionRepository.findForProgress(
                executionId, missionId
        )).thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        clientId
                )
        );

        verify(executionRepository, never())
                .save(any(MissionExecution.class));
    }
}