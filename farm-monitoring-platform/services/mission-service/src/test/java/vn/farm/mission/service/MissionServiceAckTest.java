package vn.farm.mission.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionAckRequest;
import vn.farm.mission.dto.response.MissionAckResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.repository.MissionExecutionRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MissionServiceAckTest {

    @Mock
    MissionExecutionRepository missionExecutionRepository;

    @InjectMocks
    MissionService missionService;

    UUID missionId;
    UUID executionId;
    Mission mission;
    MissionExecution execution;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        mission = new Mission();
        mission.setMissionId(missionId);
        mission.setStatus(MissionStatus.RUNNING);

        execution = MissionExecution.builder()
                .executionId(executionId)
                .mission(mission)
                .status(MissionExecutionStatus.WAITING_ACK)
                .assignedClientId("uav-simulator-test")
                .ackDeadline(Instant.now().plusSeconds(90))
                .build();
    }

    @Test
    void ackMission_success() {
        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.of(execution));

        when(missionExecutionRepository.save(any(MissionExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        MissionAckResponse response = missionService.ackMission(
                missionId,
                request,
                "uav-simulator-test"
        );

        assertEquals("EXECUTING", response.getStatus());
        assertEquals(MissionExecutionStatus.EXECUTING, execution.getStatus());
        assertNotNull(execution.getAcknowledgedAt());

        verify(missionExecutionRepository).save(execution);
    }

    @Test
    void ackMission_duplicate_shouldReturnPreviousAck() {
        Instant acknowledgedAt = Instant.now().minusSeconds(10);

        execution.setStatus(MissionExecutionStatus.EXECUTING);
        execution.setAcknowledgedAt(acknowledgedAt);

        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.of(execution));

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        MissionAckResponse response = missionService.ackMission(
                missionId, request, "uav-simulator-test"
        );

        assertEquals("EXECUTING", response.getStatus());
        assertEquals(acknowledgedAt, response.getAcknowledgedAt());

        verify(missionExecutionRepository, never()).save(any());
    }

    @Test
    void ackMission_expired_shouldFail() {
        execution.setAckDeadline(Instant.now().minusSeconds(10));

        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.of(execution));

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        assertThrows(BusinessException.class, () ->
                missionService.ackMission(
                        missionId, request, "uav-simulator-test"
                )
        );

        verify(missionExecutionRepository, never()).save(any());
    }

    @Test
    void ackMission_wrongClient_shouldFail() {
        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.of(execution));

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        assertThrows(BusinessException.class, () ->
                missionService.ackMission(
                        missionId, request, "wrong-simulator"
                )
        );

        verify(missionExecutionRepository, never()).save(any());
    }

    @Test
    void ackMission_executionNotFound_shouldFail() {
        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.empty());

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        assertThrows(BusinessException.class, () ->
                missionService.ackMission(
                        missionId, request, "uav-simulator-test"
                )
        );
    }

    @Test
    void ackMission_missionNotRunning_shouldFail() {
        mission.setStatus(MissionStatus.PLANNED);

        when(missionExecutionRepository.findForAck(executionId, missionId))
                .thenReturn(Optional.of(execution));

        MissionAckRequest request = new MissionAckRequest();
        request.setExecutionId(executionId);

        assertThrows(BusinessException.class, () ->
                missionService.ackMission(
                        missionId, request, "uav-simulator-test"
                )
        );

        verify(missionExecutionRepository, never()).save(any());
    }
}