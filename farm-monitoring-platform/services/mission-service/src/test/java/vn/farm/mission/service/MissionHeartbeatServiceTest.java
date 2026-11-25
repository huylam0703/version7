package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionHeartbeatRequest;
import vn.farm.mission.dto.response.MissionHeartbeatResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.repository.MissionExecutionRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionHeartbeatServiceTest {

    private MissionExecutionRepository executionRepository;
    private MissionHeartbeatService heartbeatService;
    private MissionRecoveryOwnershipValidator ownershipValidator;
    private MissionExecutionLockService lockService;

    private UUID missionId;
    private UUID executionId;
    private String clientId;

    private Mission mission;
    private MissionExecution execution;
    private MissionHeartbeatRequest request;

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

        when(mission.getStatus())
                .thenReturn(MissionStatus.RUNNING);

        execution = MissionExecution.builder()
                .executionId(executionId)
                .mission(mission)
                .status(MissionExecutionStatus.EXECUTING)
                .assignedClientId(clientId)
                .build();

        request = MissionHeartbeatRequest.builder()
                .executionId(executionId)
                .instanceId(UUID.randomUUID())
                .recoveryGeneration(1L)
                .build();

        when(lockService.lock(missionId, executionId))
                .thenReturn(execution);
    }

    @Test
    void shouldUpdateHeartbeatSuccessfully() {

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

        verify(ownershipValidator).validate(
                execution,
                request.getInstanceId(),
                request.getRecoveryGeneration()
        );
    }

    @Test
    void shouldRejectInvalidClientId() {

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

        execution.setStatus(
                MissionExecutionStatus.WAITING_ACK
        );

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

        when(mission.getStatus())
                .thenReturn(MissionStatus.COMPLETED);

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

        when(lockService.lock(missionId, executionId))
                .thenThrow(BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Mission Execution"
                ));

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