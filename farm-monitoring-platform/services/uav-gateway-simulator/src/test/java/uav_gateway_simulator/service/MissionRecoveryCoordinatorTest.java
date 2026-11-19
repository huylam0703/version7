
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.dto.RecoverableMissionResponse;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionRecoveryCoordinatorTest {

    @Mock
    private MissionClient missionClient;

    @Mock
    private MissionProcessingService missionProcessingService;

    @Mock
    private MissionExecutionTracker executionTracker;

    @InjectMocks
    private MissionRecoveryCoordinator recoveryCoordinator;

    private RecoverableMissionResponse mission;

    @BeforeEach
    void setUp() {
        mission = new RecoverableMissionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    @Test
    void shouldRecoverMissionWhenSimulatorStarts() {

        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of(mission));

        when(missionProcessingService.recover(
                any(MissionReadyPayload.class)
        )).thenReturn(true);

        recoveryCoordinator.recoverAvailableMissions();

        verify(missionProcessingService, times(1))
                .recover(argThat(payload ->
                        mission.missionId().equals(payload.getMissionId())
                                && mission.executionId().equals(payload.getExecutionId())
                                && mission.farmId().equals(payload.getFarmId())
                                && mission.uavId().equals(payload.getUavId())
                                && mission.gatewayId().equals(payload.getGatewayId())
                ));
    }

    @Test
    void shouldNotRecoverWhenNoMissionsAvailable() {

        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of());

        recoveryCoordinator.recoverAvailableMissions();

        verify(missionProcessingService, never())
                .recover(any(MissionReadyPayload.class));
    }

    @Test
    void shouldSkipAlreadyCompletedExecution() {

        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of(mission));

        when(executionTracker.getStatus(mission.executionId()))
                .thenReturn(MissionExecutionTracker.Status.COMPLETED);

        recoveryCoordinator.recoverAvailableMissions();

        verify(missionProcessingService, never())
                .recover(any(MissionReadyPayload.class));
    }

    @Test
    void shouldSkipProcessingExecution() {

        when(missionClient.getRecoverableMissions())
                .thenReturn(List.of(mission));

        when(executionTracker.getStatus(mission.executionId()))
                .thenReturn(MissionExecutionTracker.Status.PROCESSING);

        recoveryCoordinator.recoverAvailableMissions();

        verify(missionProcessingService, never())
                .recover(any(MissionReadyPayload.class));
    }
}
