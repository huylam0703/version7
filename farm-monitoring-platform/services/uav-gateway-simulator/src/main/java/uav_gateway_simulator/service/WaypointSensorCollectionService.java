
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import uav_gateway_simulator.client.Esp32Client;
import uav_gateway_simulator.dto.request.MissionNodeProgressRequest;
import uav_gateway_simulator.dto.response.WaypointResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaypointSensorCollectionService {

    private final Esp32Client esp32Client;
    private final MissionCancellationService cancellationService;
    private final RecoveryLeaseRegistry leaseRegistry;

    public List<MissionNodeProgressRequest> collect(
            UUID missionId,
            UUID executionId,
            WaypointResponse waypoint
    ) {
        List<MissionNodeProgressRequest> results = new ArrayList<>();

        List<UUID> nodeIds = waypoint.nodeIds() == null
                ? List.of()
                : waypoint.nodeIds();

        for (UUID nodeId : nodeIds) {
            cancellationService.checkNotCancelled(missionId);
            leaseRegistry.getForWrite(executionId);

            try {
                JsonNode data = esp32Client.readByNodeId(nodeId);

                if (data == null || !data.isObject()
                        || data.isEmpty()) {
                    throw new IllegalStateException(
                            "Invalid ESP32 response"
                    );
                }

                results.add(new MissionNodeProgressRequest(
                        nodeId,
                        "SUCCESS",
                        1,
                        1,
                        Instant.now()
                ));

                log.info(
                        "ESP32 collected: waypointSeq={}, nodeId={}, data={}",
                        waypoint.seq(),
                        nodeId,
                        data
                );

            } catch (Exception e) {
                log.warn(
                        "ESP32 collection failed: waypointSeq={}, nodeId={}, error={}",
                        waypoint.seq(),
                        nodeId,
                        e.getMessage()
                );

                results.add(new MissionNodeProgressRequest(
                        nodeId,
                        "FAILED",
                        0,
                        1,
                        null
                ));
            }
        }

        return results;
    }
}
