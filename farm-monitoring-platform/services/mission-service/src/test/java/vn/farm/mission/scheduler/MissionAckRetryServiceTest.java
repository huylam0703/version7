package vn.farm.mission.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
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

class MissionAckRetryServiceTest {

    @Mock
    MissionExecutionRepository executionRepository;

    @Mock
    MissionRepository missionRepository;

    @Mock
    MissionStatusLogRepository statusLogRepository;

    @Mock
    MissionOutboxService outboxService;

    @InjectMocks
    MissionAckRetryService retryService;

    UUID executionId;
    Mission mission;
    MissionExecution execution;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        executionId = UUID.randomUUID();

        mission = new Mission();
        mission.setMissionId(UUID.randomUUID());
        mission.setStatus(MissionStatus.RUNNING);

        execution = MissionExecution.builder()
                .executionId(executionId)
                .mission(mission)
                .status(MissionExecutionStatus.WAITING_ACK)
                .startedAt(Instant.now().minusSeconds(35))
                .ackDeadline(Instant.now().plusSeconds(55))
                .notificationAttempts(1)
                .build();

        when(executionRepository.findByIdAndStatusForUpdate(
                executionId,
                MissionExecutionStatus.WAITING_ACK
        )).thenReturn(Optional.of(execution));
    }

    @Test
    void retryAfter30Seconds_shouldCreateOutboxEvent() {

        retryService.processExecution(executionId);

        assertEquals(2, execution.getNotificationAttempts());

        verify(outboxService).saveEvent(
                eq(FarmEvents.MISSION_READY),
                any()
        );

        verify(executionRepository).save(execution);
    }
    @Test
    void before30Seconds_shouldNotRetry() {
        execution.setStartedAt(Instant.now().minusSeconds(15));

        retryService.processExecution(executionId);

        assertEquals(1, execution.getNotificationAttempts());

        verify(outboxService, never()).saveEvent(anyString(), any());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void retryAfter60Seconds_shouldCreateOutboxEvent() {
        execution.setStartedAt(Instant.now().minusSeconds(65));
        execution.setAckDeadline(Instant.now().plusSeconds(25));
        execution.setNotificationAttempts(2);

        retryService.processExecution(executionId);

        assertEquals(3, execution.getNotificationAttempts());

        verify(outboxService).saveEvent(
                eq(FarmEvents.MISSION_READY),
                any()
        );

        verify(executionRepository).save(execution);
    }

    @Test
    void after90Seconds_shouldFailMissionAndExecution() {
        execution.setStartedAt(Instant.now().minusSeconds(95));
        execution.setAckDeadline(Instant.now().minusSeconds(5));

        retryService.processExecution(executionId);

        assertEquals(MissionExecutionStatus.FAILED, execution.getStatus());
        assertEquals(MissionStatus.FAILED, mission.getStatus());
        assertNotNull(mission.getEndedAt());

        verify(executionRepository).save(execution);
        verify(missionRepository).save(mission);
        verify(statusLogRepository).save(any(MissionStatusLog.class));
        verify(outboxService).saveEvent(
                eq(FarmEvents.MISSION_STATUS_CHANGED),
                any(MissionStatusChangedPayload.class)
        );
    }

    @Test
    void alreadyAcknowledged_shouldNotRetry() {
        execution.setStatus(MissionExecutionStatus.EXECUTING);

        when(executionRepository.findByIdAndStatusForUpdate(
                executionId,
                MissionExecutionStatus.WAITING_ACK
        )).thenReturn(Optional.empty());

        retryService.processExecution(executionId);

        verify(outboxService, never()).saveEvent(anyString(), any());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void cancelledMission_shouldNotRetryOrFail() {
        mission.setStatus(MissionStatus.CANCELLED);

        execution.setStartedAt(Instant.now().minusSeconds(95));
        execution.setAckDeadline(Instant.now().minusSeconds(5));

        retryService.processExecution(executionId);

        assertEquals(MissionStatus.CANCELLED, mission.getStatus());
        assertEquals(MissionExecutionStatus.WAITING_ACK, execution.getStatus());

        verify(outboxService, never()).saveEvent(anyString(), any());
        verify(missionRepository, never()).save(any());
        verify(executionRepository, never()).save(any());
    }
}