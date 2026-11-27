package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FlightTelemetryConfigurationTest {
    private final UUID missionId = UUID.randomUUID();
    private final UUID executionId = UUID.randomUUID();
    private FlightTelemetryService telemetry;
    private FlightMovementService movement;

    @BeforeEach
    void setUp() {
        configure(50L, 2.0);
    }

    private void configure(long interval, double drain) {
        var properties = new FlightTelemetryProperties(true, interval, drain, 80.0, 1000L, 0L);
        telemetry = new FlightTelemetryService(properties);
        movement = new FlightMovementService(new FlightPositionCalculator(), telemetry,
                new FlightOwnershipService(new RecoveryLeaseRegistry()), properties);
    }

    private WaypointResponse waypoint(int seq) {
        return new WaypointResponse(UUID.randomUUID(), seq, new BigDecimal("10.75"),
                new BigDecimal("106.68"), new BigDecimal("20"), "PENDING", List.of());
    }

    private FlightTelemetry latest() {
        return telemetry.getLatest(missionId).orElseThrow();
    }

    @Test
    void shouldUseConfiguredInitialBattery() {
        telemetry.initialize(missionId, executionId, 10.75, 106.68, 20.0);
        assertEquals(80.0, latest().batteryPercent(), 0.000001);
    }

    @Test
    void shouldUseConfiguredBatteryOnRecoveryWithoutSnapshot() {
        movement.restoreForRecovery(missionId, executionId, waypoint(1), waypoint(2));
        assertEquals(80.0, latest().batteryPercent(), 0.000001);
        assertEquals(FlightStatus.RECOVERING, latest().flightStatus());
    }

    @Test
    void shouldPreserveExistingBatteryOnRecovery() {
        telemetry.initialize(missionId, executionId, 10.75, 106.68, 20.0);
        telemetry.consumeBattery(missionId, executionId, 10.0);
        movement.restoreForRecovery(missionId, executionId, waypoint(1), waypoint(2));
        assertEquals(70.0, latest().batteryPercent(), 0.000001);
    }

    @Test
    void shouldUseConfiguredDrain() {
        movement.move(missionId, executionId, waypoint(1), waypoint(2), 250L);
        assertEquals(79.5, latest().batteryPercent(), 0.000001);
    }

    @Test
    void shouldAllowZeroDrain() {
        configure(50L, 0.0);
        movement.move(missionId, executionId, waypoint(1), waypoint(2), 100L);
        assertEquals(80.0, latest().batteryPercent(), 0.000001);
    }

    @Test
    void shouldUpdateDuringFlightWithShortConfiguredInterval() {
        AtomicInteger checks = new AtomicInteger();
        movement.move(missionId, executionId, waypoint(1), waypoint(2), 500L,
                () -> checks.incrementAndGet());
        assertTrue(checks.get() > 3, "Expected intermediate updates with 50 ms interval");
        assertEquals(FlightStatus.COLLECTING, latest().flightStatus());
    }
}
