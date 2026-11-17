package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.MissionProgressRequest;
import uav_gateway_simulator.dto.MissionProgressResponse;
import uav_gateway_simulator.dto.MissionResumePlan;
import uav_gateway_simulator.dto.WaypointResponse;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaypointSimulationService {

    private final MissionClient missionClient;

    @Value("${simulator.flight.waypoint-delay-ms:3000}")
    private long waypointDelayMs;

    public void simulate(
            UUID missionId,
            UUID executionId,
            List<WaypointResponse> waypoints
    ) {
        if (waypoints == null || waypoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mission has no waypoints"
            );
        }

        List<WaypointResponse> sortedWaypoints = waypoints.stream()
                .sorted(Comparator.comparingInt(WaypointResponse::seq))
                .toList();

        long progressSequence = 1;

        log.info(
                "Starting waypoint simulation: missionId={}, executionId={}, totalWaypoints={}",
                missionId,
                executionId,
                sortedWaypoints.size()
        );

        for (WaypointResponse waypoint : sortedWaypoints) {

            try {
                Thread.sleep(waypointDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Waypoint simulation interrupted",
                        e
                );
            }

            log.info(
                    "Waypoint reached: missionId={}, waypointId={}, seq={}",
                    missionId,
                    waypoint.waypointId(),
                    waypoint.seq()
            );

            if (waypoint.seq() < 0 ||
                    waypoint.seq() > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Invalid waypoint sequence: " + waypoint.seq()
                );
            }

            MissionProgressRequest request =
                    new MissionProgressRequest(
                            executionId,
                            progressSequence,
                            (short) waypoint.seq(),
                            Instant.now(),
                            List.of()
                    );

            MissionProgressResponse response =
                    missionClient.updateProgress(
                            missionId,
                            request
                    );


            if (response == null
                    || !missionId.equals(response.missionId())
                    || !executionId.equals(response.executionId())
                    || response.lastConfirmedSequence() != progressSequence) {

                throw new IllegalStateException(
                        "Invalid progress confirmation"
                );
            }


            log.info(
                    "Progress confirmed: missionId={}, executionId={}, sequence={}, waypointSeq={}",
                    missionId,
                    executionId,
                    progressSequence,
                    waypoint.seq()
            );

            progressSequence++;
        }

        log.info(
                "Waypoint simulation completed: missionId={}, executionId={}",
                missionId,
                executionId
        );
    }


    public void simulateResume(
            UUID missionId,
            UUID executionId,
            List<WaypointResponse> waypoints,
            MissionResumePlan plan
    ) {
        if (plan == null
                || !missionId.equals(plan.missionId())
                || !executionId.equals(plan.executionId())) {
            throw new IllegalArgumentException(
                    "Invalid mission resume plan"
            );
        }

        if (waypoints == null || waypoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mission has no waypoints"
            );
        }

        if (plan.nextProgressSequence() < 1) {
            throw new IllegalArgumentException(
                    "Invalid resume progress sequence"
            );
        }

        List<WaypointResponse> sortedWaypoints = waypoints.stream()
                .sorted(Comparator.comparingInt(WaypointResponse::seq))
                .toList();

        if (plan.allWaypointsReached()) {
            if (plan.nextWaypointSeq() != null) {
                throw new IllegalStateException(
                        "Invalid completed resume plan"
                );
            }

            log.info(
                    "All waypoints already reached: executionId={}",
                    executionId
            );
            return;
        }

        if (plan.nextWaypointSeq() == null) {
            throw new IllegalStateException(
                    "Missing resume waypoint sequence"
            );
        }

        int startIndex = -1;

        for (int i = 0; i < sortedWaypoints.size(); i++) {
            if (sortedWaypoints.get(i).seq()
                    == plan.nextWaypointSeq()) {
                startIndex = i;
                break;
            }
        }

        if (startIndex < 0) {
            throw new IllegalStateException(
                    "Resume waypoint not found in Mission Detail"
            );
        }

        long progressSequence = plan.nextProgressSequence();

        for (int i = startIndex; i < sortedWaypoints.size(); i++) {
            WaypointResponse waypoint = sortedWaypoints.get(i);

            if (waypoint.seq() < 0
                    || waypoint.seq() > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Invalid waypoint sequence: " + waypoint.seq()
                );
            }

            try {
                Thread.sleep(waypointDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Waypoint resume interrupted", e
                );
            }

            MissionProgressRequest request =
                    new MissionProgressRequest(
                            executionId,
                            progressSequence,
                            (short) waypoint.seq(),
                            Instant.now(),
                            List.of()
                    );

            MissionProgressResponse response =
                    missionClient.updateProgress(missionId, request);

            if (response == null
                    || !missionId.equals(response.missionId())
                    || !executionId.equals(response.executionId())
                    || response.lastConfirmedSequence()
                    != progressSequence
                    || response.waypointSeq() == null
                    || response.waypointSeq() != waypoint.seq()) {
                throw new IllegalStateException(
                        "Invalid resume progress confirmation"
                );
            }

            log.info(
                    "Resume waypoint confirmed: executionId={}, waypointSeq={}, progressSequence={}",
                    executionId,
                    waypoint.seq(),
                    progressSequence
            );

            progressSequence++;
        }

        log.info(
                "Waypoint resume completed: executionId={}",
                executionId
        );
    }

}