package uav_gateway_simulator.telemetry;

import java.time.Instant;
import java.util.UUID;

public record FlightTelemetry(

        UUID missionId,

        UUID executionId,

        Double latitude,

        Double longitude,

        Double altitudeM,

        Double speedMps,

        Double headingDeg,

        Double batteryPercent,

        Integer currentWaypointSeq,

        Integer targetWaypointSeq,

        FlightStatus flightStatus,

        Instant timestamp

) {
}