
package uav_gateway_simulator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionDetailResponse;
import uav_gateway_simulator.exception.MissionCancelledException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionCancellationServiceTest {

    @Mock
    private MissionClient missionClient;

    @InjectMocks
    private MissionCancellationService cancellationService;

    @Test
    void shouldDetectCancelledMission() {

        UUID missionId = UUID.randomUUID();

        MissionDetailResponse mission =
                mock(MissionDetailResponse.class);

        when(missionClient.getMissionDetail(missionId))
                .thenReturn(mission);

        when(mission.status()).thenReturn("CANCELLED");

        assertTrue(cancellationService.isCancelled(missionId));
    }

    @Test
    void shouldAllowRunningMission() {

        UUID missionId = UUID.randomUUID();

        MissionDetailResponse mission =
                mock(MissionDetailResponse.class);

        when(missionClient.getMissionDetail(missionId))
                .thenReturn(mission);

        when(mission.status()).thenReturn("RUNNING");

        assertDoesNotThrow(
                () -> cancellationService.checkNotCancelled(missionId)
        );
    }

    @Test
    void shouldStopCancelledMission() {

        UUID missionId = UUID.randomUUID();

        MissionDetailResponse mission =
                mock(MissionDetailResponse.class);

        when(missionClient.getMissionDetail(missionId))
                .thenReturn(mission);

        when(mission.status()).thenReturn("CANCELLED");

        assertThrows(
                MissionCancelledException.class,
                () -> cancellationService.checkNotCancelled(missionId)
        );
    }
}
