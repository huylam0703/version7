
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.config.FlightTelemetryProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class FlightTelemetryService {

    private final ConcurrentMap<UUID, FlightTelemetry> telemetryStore =
            new ConcurrentHashMap<>();
    private final FlightTelemetryProperties telemetryProperties;

    public FlightTelemetry initialize(
            UUID missionId,
            UUID executionId,
            double latitude,
            double longitude,
            double altitudeM
    ) {
        Objects.requireNonNull(missionId, "Mission ID is required");
        Objects.requireNonNull(executionId, "Execution ID is required");

        return telemetryStore.compute(missionId, (id, existing) -> {

            if (existing != null
                    && executionId.equals(existing.executionId())) {
                return existing;
            }

            return new FlightTelemetry(
                    missionId,
                    executionId,
                    latitude,
                    longitude,
                    altitudeM,
                    0.0,
                    0.0,
                    telemetryProperties.initialBatteryPercent(),
                    null,
                    null,
                    FlightStatus.READY,
                    Instant.now()
            );
        });
    }

    public FlightTelemetry updatePosition(
            UUID missionId,
            UUID executionId,
            double latitude,
            double longitude,
            double altitudeM,
            double speedMps,
            double headingDeg,
            Integer currentWaypointSeq,
            Integer targetWaypointSeq
    ) {
        return telemetryStore.compute(missionId, (id, current) -> {

            validateExecution(current, executionId);
            validateActive(current);

            // Không ghi đè trạng thái mất kết nối.
            if (current.flightStatus() == FlightStatus.DISCONNECTED) {
                return current;
            }

            return new FlightTelemetry(
                    missionId,
                    executionId,
                    latitude,
                    longitude,
                    altitudeM,
                    Math.max(0.0, speedMps),
                    normalizeHeading(headingDeg),
                    current.batteryPercent(),
                    currentWaypointSeq,
                    targetWaypointSeq,
                    FlightStatus.FLYING,
                    Instant.now()
            );
        });
    }

    public FlightTelemetry updateStatus(
            UUID missionId,
            UUID executionId,
            FlightStatus status
    ) {
        Objects.requireNonNull(status, "Flight status is required");

        return telemetryStore.compute(missionId, (id, current) -> {

            validateExecution(current, executionId);

            if (isTerminal(current.flightStatus())) {
                return current;
            }
            if (current.flightStatus() == FlightStatus.DISCONNECTED
                    && (status == FlightStatus.FLYING
                    || status == FlightStatus.COLLECTING
                    || status == FlightStatus.READY)) {
                return current;
            }

            double speed = status == FlightStatus.FLYING
                    ? current.speedMps()
                    : 0.0;

            return new FlightTelemetry(
                    current.missionId(),
                    current.executionId(),
                    current.latitude(),
                    current.longitude(),
                    current.altitudeM(),
                    speed,
                    current.headingDeg(),
                    current.batteryPercent(),
                    current.currentWaypointSeq(),
                    isTerminal(status)
                            ? null
                            : current.targetWaypointSeq(),
                    status,
                    Instant.now()
            );
        });
    }

    public FlightTelemetry consumeBattery(
            UUID missionId,
            UUID executionId,
            double consumedPercent
    ) {
        if (!Double.isFinite(consumedPercent)
                || consumedPercent < 0) {
            throw new IllegalArgumentException(
                    "Consumed battery must be finite and non-negative"
            );
        }

        return telemetryStore.compute(missionId, (id, current) -> {

            validateExecution(current, executionId);
            validateActive(current);

// Giữ nguyên snapshot trong thời gian mất kết nối.
            if (current.flightStatus() == FlightStatus.DISCONNECTED) {
                return current;
            }

            double battery = Math.max(
                    0.0,
                    current.batteryPercent() - consumedPercent
            );

            return new FlightTelemetry(
                    current.missionId(),
                    current.executionId(),
                    current.latitude(),
                    current.longitude(),
                    current.altitudeM(),
                    current.speedMps(),
                    current.headingDeg(),
                    battery,
                    current.currentWaypointSeq(),
                    current.targetWaypointSeq(),
                    current.flightStatus(),
                    Instant.now()
            );
        });
    }

    public Optional<FlightTelemetry> getLatest(UUID missionId) {
        return Optional.ofNullable(
                telemetryStore.get(missionId)
        );
    }

    public boolean remove(
            UUID missionId,
            UUID executionId
    ) {
        FlightTelemetry current = telemetryStore.get(missionId);

        if (current == null
                || !current.executionId().equals(executionId)) {
            return false;
        }

        return telemetryStore.remove(missionId, current);
    }

    private void validateExecution(
            FlightTelemetry current,
            UUID executionId
    ) {
        if (current == null) {
            throw new IllegalStateException(
                    "Flight telemetry has not been initialized"
            );
        }

        if (!current.executionId().equals(executionId)) {
            throw new IllegalStateException(
                    "Flight telemetry execution ID mismatch"
            );
        }
    }

    private void validateActive(FlightTelemetry current) {
        if (isTerminal(current.flightStatus())) {
            throw new IllegalStateException(
                    "Flight telemetry is already finished"
            );
        }
    }

    private boolean isTerminal(FlightStatus status) {
        return status == FlightStatus.COMPLETED
                || status == FlightStatus.PARTIAL
                || status == FlightStatus.FAILED
                || status == FlightStatus.CANCELLED;
    }

    private double normalizeHeading(double heading) {
        return ((heading % 360.0) + 360.0) % 360.0;
    }

    public void updateStatusIfPresent(
            UUID missionId,
            UUID executionId,
            FlightStatus status
    ) {
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(status);

        telemetryStore.computeIfPresent(missionId, (id, current) -> {

            if (!executionId.equals(current.executionId())) {
                return current;
            }

            if (isTerminal(current.flightStatus())) {
                return current;
            }
            if (current.flightStatus() == FlightStatus.DISCONNECTED
                    && (status == FlightStatus.FLYING
                    || status == FlightStatus.COLLECTING
                    || status == FlightStatus.READY)) {
                return current;
            }

            double speed = status == FlightStatus.FLYING
                    ? current.speedMps()
                    : 0.0;

            return new FlightTelemetry(
                    current.missionId(),
                    current.executionId(),
                    current.latitude(),
                    current.longitude(),
                    current.altitudeM(),
                    speed,
                    current.headingDeg(),
                    current.batteryPercent(),
                    current.currentWaypointSeq(),
                    isTerminal(status)
                            ? null
                            : current.targetWaypointSeq(),
                    status,
                    Instant.now()
            );
        });
    }

    public void reconnectIfDisconnected(
            UUID missionId,
            UUID executionId
    ) {
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(executionId);

        telemetryStore.computeIfPresent(missionId, (id, current) -> {

            if (!executionId.equals(current.executionId())) {
                return current;
            }

            if (current.flightStatus() != FlightStatus.DISCONNECTED) {
                return current;
            }

            return new FlightTelemetry(
                    current.missionId(),
                    current.executionId(),
                    current.latitude(),
                    current.longitude(),
                    current.altitudeM(),
                    0.0,
                    current.headingDeg(),
                    current.batteryPercent(),
                    current.currentWaypointSeq(),
                    current.targetWaypointSeq(),
                    FlightStatus.RECOVERING,
                    Instant.now()
            );
        });
    }


    public FlightTelemetry restoreForRecovery(
            UUID missionId,
            UUID executionId,
            double latitude,
            double longitude,
            double altitudeM,
            Integer currentWaypointSeq,
            Integer targetWaypointSeq
    ) {
        Objects.requireNonNull(missionId);
        Objects.requireNonNull(executionId);

        return telemetryStore.compute(missionId, (id, existing) -> {

            if (existing != null) {

                validateExecution(existing, executionId);

                if (isTerminal(existing.flightStatus())) {
                    return existing;
                }
            }

            double battery = existing == null
                    ? telemetryProperties.initialBatteryPercent()
                    : existing.batteryPercent();

            double heading = existing == null
                    ? 0.0
                    : existing.headingDeg();

            return new FlightTelemetry(
                    missionId,
                    executionId,
                    latitude,
                    longitude,
                    altitudeM,
                    0.0,
                    heading,
                    battery,
                    currentWaypointSeq,
                    targetWaypointSeq,
                    FlightStatus.RECOVERING,
                    Instant.now()
            );
        });
    }

}
