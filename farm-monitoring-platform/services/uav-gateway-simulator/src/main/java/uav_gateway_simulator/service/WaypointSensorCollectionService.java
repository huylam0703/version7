
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import uav_gateway_simulator.client.Esp32Client;
import uav_gateway_simulator.dto.request.MissionNodeProgressRequest;
import uav_gateway_simulator.dto.request.SensorReadingRequest;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.exception.MissionCancelledException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaypointSensorCollectionService {

    private final Esp32Client esp32Client;
    private final Esp32ReadingMapper readingMapper;
    private final SensorCollectionService sensorCollectionService;
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
                // 1. Read real ESP32 data
                JsonNode data = esp32Client.readByNodeId(nodeId);

                if (data == null || !data.isObject()
                        || data.isEmpty()) {
                    throw new IllegalStateException(
                            "Invalid ESP32 response"
                    );
                }

                log.info(
                        "ESP32 collected: waypointSeq={}, nodeId={}, data={}",
                        waypoint.seq(),
                        nodeId,
                        data
                );

                // 2. Map JSON into Sensor Reading DTOs
                List<SensorReadingRequest> readings =
                        readingMapper.map(nodeId, data, waypoint);

                if (readings.isEmpty()) {
                    throw new IllegalStateException(
                            "No sensor readings to upload"
                    );
                }

                leaseRegistry.getForWrite(executionId);
                var delivery = sensorCollectionService.collectAndSend(readings);

                results.add(new MissionNodeProgressRequest(
                        nodeId,
                        "SUCCESS",
                        readings.size(),
                        1,
                        Instant.now()
                ));

                log.info(
                        "ESP32 collection completed: waypointSeq={}, nodeId={}, recordsCount={}, delivery={}",
                        waypoint.seq(),
                        nodeId,
                        readings.size(),
                        delivery
                );

            } catch (MissionCancelledException e) {
                throw e;

            } catch (Exception e) {
                // Do not turn cancellation or lease loss
                // into an ordinary sensor failure.
                cancellationService.checkNotCancelled(missionId);
                leaseRegistry.getForWrite(executionId);

                log.warn(
                        "ESP32 collection/upload failed: waypointSeq={}, nodeId={}, error={}",
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
