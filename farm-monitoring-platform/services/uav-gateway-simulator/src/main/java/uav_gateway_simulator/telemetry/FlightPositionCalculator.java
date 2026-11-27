
package uav_gateway_simulator.telemetry;

import org.springframework.stereotype.Component;

@Component
public class FlightPositionCalculator {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public double interpolate(
            double start,
            double end,
            double progress
    ) {
        double ratio = clampProgress(progress);

        return start + (end - start) * ratio;
    }

    public double calculateDistanceM(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        double lat1 = Math.toRadians(startLatitude);
        double lat2 = Math.toRadians(endLatitude);

        double deltaLat = Math.toRadians(
                endLatitude - startLatitude
        );

        double deltaLon = Math.toRadians(
                endLongitude - startLongitude
        );

        double a =
                Math.sin(deltaLat / 2)
                        * Math.sin(deltaLat / 2)
                        + Math.cos(lat1)
                        * Math.cos(lat2)
                        * Math.sin(deltaLon / 2)
                        * Math.sin(deltaLon / 2);

        a = Math.max(0.0, Math.min(1.0, a));

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );

        return EARTH_RADIUS_M * c;
    }

    public double calculateHeadingDeg(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        double lat1 = Math.toRadians(startLatitude);
        double lat2 = Math.toRadians(endLatitude);

        double deltaLon = Math.toRadians(
                endLongitude - startLongitude
        );

        double y = Math.sin(deltaLon)
                * Math.cos(lat2);

        double x = Math.cos(lat1)
                * Math.sin(lat2)
                - Math.sin(lat1)
                * Math.cos(lat2)
                * Math.cos(deltaLon);

        double heading = Math.toDegrees(
                Math.atan2(y, x)
        );

        return (heading + 360.0) % 360.0;
    }

    public double calculateSpeedMps(
            double distanceM,
            long durationMs
    ) {
        if (durationMs <= 0 || distanceM <= 0) {
            return 0.0;
        }

        double durationSeconds = durationMs / 1000.0;

        return distanceM / durationSeconds;
    }

    public double clampProgress(double progress) {
        if (!Double.isFinite(progress)) {
            throw new IllegalArgumentException(
                    "Progress must be finite"
            );
        }

        return Math.max(0.0, Math.min(1.0, progress));
    }
}
