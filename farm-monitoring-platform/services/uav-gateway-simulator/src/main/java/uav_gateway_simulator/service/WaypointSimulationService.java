
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.request.MissionNodeProgressRequest;
import uav_gateway_simulator.dto.request.MissionProgressRequest;
import uav_gateway_simulator.dto.response.MissionProgressResponse;
import uav_gateway_simulator.dto.MissionResumePlan;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.telemetry.FlightMovementService;
import jakarta.annotation.PostConstruct;
import uav_gateway_simulator.telemetry.FlightOwnershipService;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WaypointSimulationService {

    private final MissionClient missionClient;
    private final RecoveryLeaseRegistry leaseRegistry;
    private final MissionCancellationService cancellationService;
    private final WaypointSensorCollectionService sensorCollectionService;

    // UAV-09: Realtime Flight Telemetry
    private final FlightMovementService flightMovementService;
    private final FlightOwnershipService flightOwnershipService;

    @Value("${simulator.flight.waypoint-delay-ms:3000}")
    private long waypointDelayMs;

    public void simulate(
            UUID missionId,
            UUID executionId,
            List<WaypointResponse> waypoints
    ) {
        simulate(
                missionId,
                executionId,
                waypoints,
                flightOwnershipService.capture(executionId)
        );
    }

    public void simulate(
            UUID missionId,
            UUID executionId,
            List<WaypointResponse> waypoints,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnership(executionId, ownershipToken);

        if (waypoints == null || waypoints.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mission has no waypoints"
            );
        }

        List<WaypointResponse> sortedWaypoints = waypoints.stream()
                .sorted(Comparator.comparingInt(WaypointResponse::seq))
                .toList();

        long progressSequence = 1;

        WaypointResponse previousWaypoint = null;

        log.info(
                "Starting waypoint simulation: missionId={}, executionId={}, totalWaypoints={}",
                missionId,
                executionId,
                sortedWaypoints.size()
        );

        for (WaypointResponse waypoint : sortedWaypoints) {

            cancellationService.checkNotCancelled(missionId);

            if (waypoint.seq() < 0
                    || waypoint.seq() > Short.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Invalid waypoint sequence: " + waypoint.seq()
                );
            }

            // UAV-09: Simulate realtime flight movement.
            flightMovementService.move(
                    missionId,
                    executionId,
                    previousWaypoint,
                    waypoint,
                    waypointDelayMs,
                    () -> {
                        cancellationService.checkNotCancelled(missionId);
                        flightOwnershipService.validate(ownershipToken);
                    },
                    ownershipToken
            );

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            log.info(
                    "Waypoint reached: missionId={}, waypointId={}, seq={}",
                    missionId,
                    waypoint.waypointId(),
                    waypoint.seq()
            );

            List<MissionNodeProgressRequest> nodeResults =
                    sensorCollectionService.collect(
                            missionId,
                            executionId,
                            waypoint
                    );

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            MissionProgressRequest request =
                    new MissionProgressRequest(
                            executionId,
                            progressSequence,
                            (short) waypoint.seq(),
                            Instant.now(),
                            nodeResults
                    );

            MissionProgressResponse response =
                    missionClient.updateProgress(
                            missionId,
                            request
                    );
            flightOwnershipService.validate(ownershipToken);

            if (response == null
                    || !missionId.equals(response.missionId())
                    || !executionId.equals(response.executionId())
                    || response.lastConfirmedSequence() != progressSequence
                    || response.waypointSeq() == null
                    || response.waypointSeq().intValue() != waypoint.seq()) {

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

            // Only advance after Mission Service confirms progress.
            previousWaypoint = waypoint;

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
        simulateResume(
                missionId,
                executionId,
                waypoints,
                plan,
                flightOwnershipService.capture(executionId)
        );
    }

    public void simulateResume(
            UUID missionId,
            UUID executionId,
            List<WaypointResponse> waypoints,
            MissionResumePlan plan,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnership(executionId, ownershipToken);
        if (plan == null
                || !missionId.equals(plan.missionId())
                || !executionId.equals(plan.executionId())) {
            throw new IllegalArgumentException(
                    "Invalid mission resume plan"
            );
        }


        if (!ownershipToken.isRecovery()) {
            throw new IllegalStateException(
                    "Recovery claim required: " + executionId
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

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            WaypointResponse lastWaypoint =
                    sortedWaypoints.get(
                            sortedWaypoints.size() - 1
                    );

            flightMovementService.restoreForRecovery(
                    missionId,
                    executionId,
                    lastWaypoint,
                    null,
                    ownershipToken
            );

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

        WaypointResponse previousWaypoint =
                startIndex > 0
                        ? sortedWaypoints.get(startIndex - 1)
                        : null;

        cancellationService.checkNotCancelled(missionId);
        flightOwnershipService.validate(ownershipToken);

        // UAV-09: Restore telemetry from confirmed progress.
        flightMovementService.restoreForRecovery(
                missionId,
                executionId,
                previousWaypoint,
                sortedWaypoints.get(startIndex),
                ownershipToken
        );

        log.info(
                "[FLIGHT RECOVERY] Telemetry restored: missionId={}, executionId={}, nextWaypointSeq={}",
                missionId,
                executionId,
                sortedWaypoints.get(startIndex).seq()
        );

        for (int i = startIndex; i < sortedWaypoints.size(); i++) {

            WaypointResponse waypoint = sortedWaypoints.get(i);

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            if (waypoint.seq() < 0
                    || waypoint.seq() > Short.MAX_VALUE) {

                throw new IllegalArgumentException(
                        "Invalid waypoint sequence: "
                                + waypoint.seq()
                );
            }

            // UAV-09: Resume realtime flight movement.
            flightMovementService.move(
                    missionId,
                    executionId,
                    previousWaypoint,
                    waypoint,
                    waypointDelayMs,
                    () -> {
                        cancellationService.checkNotCancelled(missionId);

                        flightOwnershipService.validate(
                                ownershipToken
                        );
                    },
                    ownershipToken
            );

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            List<MissionNodeProgressRequest> nodeResults =
                    sensorCollectionService.collect(
                            missionId,
                            executionId,
                            waypoint
                    );

            cancellationService.checkNotCancelled(missionId);
            flightOwnershipService.validate(ownershipToken);

            MissionProgressRequest request =
                    new MissionProgressRequest(
                            executionId,
                            progressSequence,
                            (short) waypoint.seq(),
                            Instant.now(),
                            nodeResults
                    );

            MissionProgressResponse response =
                    missionClient.updateProgress(
                            missionId,
                            request
                    );

            // Reject stale worker after receiving response.
            flightOwnershipService.validate(ownershipToken);

            if (response == null
                    || !missionId.equals(response.missionId())
                    || !executionId.equals(response.executionId())
                    || response.lastConfirmedSequence()
                    != progressSequence
                    || response.waypointSeq() == null
                    || response.waypointSeq().intValue()
                    != waypoint.seq()) {

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

            previousWaypoint = waypoint;

            progressSequence++;
        }

        log.info(
                "Waypoint resume completed: missionId={}, executionId={}",
                missionId,
                executionId
        );
    }


    @PostConstruct
    public void init() {
        log.info(
                "[SIMULATOR CONFIG] waypointDelayMs={}",
                waypointDelayMs
        );
    }

    private void validateOwnership(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        if (executionId == null
                || ownershipToken == null
                || !executionId.equals(
                ownershipToken.executionId()
        )) {

            throw new IllegalArgumentException(
                    "Waypoint ownership token mismatch"
            );
        }

        flightOwnershipService.validate(ownershipToken);
    }
}
