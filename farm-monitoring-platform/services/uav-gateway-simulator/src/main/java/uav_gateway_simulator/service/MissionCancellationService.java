
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionDetailResponse;
import uav_gateway_simulator.exception.MissionCancelledException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionCancellationService {

    private final MissionClient missionClient;

    public boolean isCancelled(UUID missionId) {

        MissionDetailResponse mission =
                missionClient.getMissionDetail(missionId);

        if (mission == null) {
            throw new IllegalStateException(
                    "Mission Detail response is empty"
            );
        }

        return "CANCELLED".equals(mission.status());
    }

    public void checkNotCancelled(UUID missionId) {

        if (isCancelled(missionId)) {
            throw new MissionCancelledException(
                    "Mission has been cancelled: " + missionId
            );
        }
    }
}
