
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlightPositionCalculatorTest {

    private final FlightPositionCalculator calculator =
            new FlightPositionCalculator();

    @Test
    void shouldReturnStartPosition() {
        assertEquals(
                10.0,
                calculator.interpolate(10, 20, 0),
                0.000001
        );
    }

    @Test
    void shouldReturnMiddlePosition() {
        assertEquals(
                15.0,
                calculator.interpolate(10, 20, 0.5),
                0.000001
        );
    }

    @Test
    void shouldReturnEndPosition() {
        assertEquals(
                20.0,
                calculator.interpolate(10, 20, 1),
                0.000001
        );
    }

    @Test
    void shouldClampNegativeProgress() {
        assertEquals(
                10.0,
                calculator.interpolate(10, 20, -0.5),
                0.000001
        );
    }

    @Test
    void shouldClampProgressAboveOne() {
        assertEquals(
                20.0,
                calculator.interpolate(10, 20, 1.5),
                0.000001
        );
    }

    @Test
    void shouldRejectNonFiniteProgress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.clampProgress(Double.NaN)
        );
    }

    @Test
    void shouldCalculateZeroDistanceForSamePosition() {
        assertEquals(
                0.0,
                calculator.calculateDistanceM(
                        10.75, 106.68,
                        10.75, 106.68
                ),
                0.000001
        );
    }

    @Test
    void shouldCalculateDistanceBetweenCoordinates() {
        double distance = calculator.calculateDistanceM(
                0.0, 0.0,
                0.0, 0.001
        );

        assertEquals(111.195, distance, 0.1);
    }

    @Test
    void shouldCalculateNorthHeading() {
        assertEquals(
                0.0,
                calculator.calculateHeadingDeg(
                        0, 0, 1, 0
                ),
                0.000001
        );
    }

    @Test
    void shouldCalculateEastHeading() {
        assertEquals(
                90.0,
                calculator.calculateHeadingDeg(
                        0, 0, 0, 1
                ),
                0.000001
        );
    }

    @Test
    void shouldCalculateSouthHeading() {
        assertEquals(
                180.0,
                calculator.calculateHeadingDeg(
                        0, 0, -1, 0
                ),
                0.000001
        );
    }

    @Test
    void shouldCalculateWestHeading() {
        assertEquals(
                270.0,
                calculator.calculateHeadingDeg(
                        0, 0, 0, -1
                ),
                0.000001
        );
    }

    @Test
    void shouldCalculateSpeed() {
        assertEquals(
                10.0,
                calculator.calculateSpeedMps(100, 10000),
                0.000001
        );
    }

    @Test
    void shouldReturnZeroSpeedForInvalidDuration() {
        assertEquals(
                0.0,
                calculator.calculateSpeedMps(100, 0),
                0.000001
        );
    }

    @Test
    void shouldReturnZeroSpeedForZeroDistance() {
        assertEquals(
                0.0,
                calculator.calculateSpeedMps(0, 10000),
                0.000001
        );
    }
}
