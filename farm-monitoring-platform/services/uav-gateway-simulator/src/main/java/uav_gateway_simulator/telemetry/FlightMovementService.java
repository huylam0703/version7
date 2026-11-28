
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.dto.response.WaypointResponse;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FlightMovementService {

    private final FlightPositionCalculator calculator;
    private final FlightTelemetryService telemetryService;
    private final FlightOwnershipService flightOwnershipService;
    private final FlightTelemetryProperties telemetryProperties;


    public void move(
            UUID missionId,
            UUID executionId,
            WaypointResponse from,
            WaypointResponse target,
            long durationMs
    ) {
        move(
                missionId,
                executionId,
                from,
                target,
                durationMs,
                () -> {}
        );
    }

    public void move(
            UUID missionId,
            UUID executionId,
            WaypointResponse from,
            WaypointResponse target,
            long durationMs,
            Runnable executionGuard
    ) {
        move(
                missionId,
                executionId,
                from,
                target,
                durationMs,
                executionGuard,
                flightOwnershipService.capture(executionId)
        );
    }

    public void move(
            UUID missionId,
            UUID executionId,
            WaypointResponse from,
            WaypointResponse target,
            long durationMs,
            Runnable executionGuard,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnershipToken(executionId, ownershipToken);

        if (target == null) {
            throw new IllegalArgumentException(
                    "Target waypoint is required"
            );
        }

        if (durationMs < 0) {
            throw new IllegalArgumentException(
                    "Flight duration must not be negative"
            );
        }

        if (executionGuard == null) {
            throw new IllegalArgumentException(
                    "Execution guard is required"
            );
        }

        WaypointResponse origin = from == null
                ? target
                : from;

        double startLat = origin.lat().doubleValue();

        double startLng = origin.lng().doubleValue();

        double startAltitude = origin.altitudeM() == null
                ? 0.0
                : origin.altitudeM().doubleValue();

        double endLat = target.lat().doubleValue();

        double endLng = target.lng().doubleValue();

        double endAltitude = target.altitudeM() == null
                ? startAltitude
                : target.altitudeM().doubleValue();

        Integer currentSeq = from == null
                ? null
                : from.seq();

        executionGuard.run();

        // Initialize telemetry with current ownership.
        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> telemetryService.initialize(
                        missionId,
                        executionId,
                        startLat,
                        startLng,
                        startAltitude
                )
        );

        double distance = calculator.calculateDistanceM(
                startLat,
                startLng,
                endLat,
                endLng
        );

        double heading = calculator.calculateHeadingDeg(
                startLat,
                startLng,
                endLat,
                endLng
        );

        double speed = calculator.calculateSpeedMps(
                distance,
                durationMs
        );

        // Update initial flight position.
        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> telemetryService.updatePosition(
                        missionId,
                        executionId,
                        startLat,
                        startLng,
                        startAltitude,
                        speed,
                        heading,
                        currentSeq,
                        target.seq()
                )
        );

        long startedAt = System.nanoTime();

        long previousElapsed = 0;

        while (previousElapsed < durationMs) {

            long elapsedBeforeSleep =
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt
                    );

            long remaining = Math.max(
                    0,
                    durationMs - elapsedBeforeSleep
            );

            sleep(
                    Math.min(
                            telemetryProperties.updateIntervalMs(),
                            remaining
                    )
            );

            executionGuard.run();

            long elapsed = Math.min(
                    durationMs,
                    TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAt
                    )
            );

            double progress =
                    (double) elapsed / durationMs;

            double latitude = calculator.interpolate(
                    startLat,
                    endLat,
                    progress
            );

            double longitude = calculator.interpolate(
                    startLng,
                    endLng,
                    progress
            );

            double altitude = calculator.interpolate(
                    startAltitude,
                    endAltitude,
                    progress
            );

            double elapsedSeconds =
                    (elapsed - previousElapsed) / 1000.0;

            // Protect position and battery updates
            // against stale ownership.
            flightOwnershipService.executeIfCurrent(
                    ownershipToken,
                    () -> {

                        telemetryService.updatePosition(
                                missionId,
                                executionId,
                                latitude,
                                longitude,
                                altitude,
                                speed,
                                heading,
                                currentSeq,
                                target.seq()
                        );

                        telemetryService.consumeBattery(
                                missionId,
                                executionId,
                                elapsedSeconds * telemetryProperties.batteryDrainPerSecond()
                        );
                    }
            );

            previousElapsed = elapsed;
        }

        executionGuard.run();

        // UAV has reached the target waypoint.
        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> {

                    telemetryService.updatePosition(
                            missionId,
                            executionId,
                            endLat,
                            endLng,
                            endAltitude,
                            0.0,
                            heading,
                            target.seq(),
                            null
                    );

                    telemetryService.updateStatus(
                            missionId,
                            executionId,
                            FlightStatus.COLLECTING
                    );
                }
        );
    }

    public void restoreForRecovery(
            UUID missionId,
            UUID executionId,
            WaypointResponse lastConfirmedWaypoint,
            WaypointResponse nextWaypoint
    ) {
        restoreForRecovery(
                missionId,
                executionId,
                lastConfirmedWaypoint,
                nextWaypoint,
                flightOwnershipService.capture(executionId)
        );
    }

    public void restoreForRecovery(
            UUID missionId,
            UUID executionId,
            WaypointResponse lastConfirmedWaypoint,
            WaypointResponse nextWaypoint,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnershipToken(executionId, ownershipToken);

        WaypointResponse origin =
                lastConfirmedWaypoint != null
                        ? lastConfirmedWaypoint
                        : nextWaypoint;

        if (origin == null) {
            throw new IllegalArgumentException(
                    "Recovery origin waypoint is required"
            );
        }

        double altitude = origin.altitudeM() == null
                ? 0.0
                : origin.altitudeM().doubleValue();

        // Restore telemetry only if ownership is current.
        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> telemetryService.restoreForRecovery(
                        missionId,
                        executionId,
                        origin.lat().doubleValue(),
                        origin.lng().doubleValue(),
                        altitude,
                        lastConfirmedWaypoint == null
                                ? null
                                : lastConfirmedWaypoint.seq(),
                        nextWaypoint == null
                                ? null
                                : nextWaypoint.seq()
                )
        );
    }

    private void validateOwnershipToken(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        if (executionId == null
                || ownershipToken == null
                || !executionId.equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Flight movement ownership mismatch"
            );
        }
    }

    private void sleep(long durationMs) {
        try {
            Thread.sleep(durationMs);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Flight movement interrupted",
                    e
            );
        }
    }
}
