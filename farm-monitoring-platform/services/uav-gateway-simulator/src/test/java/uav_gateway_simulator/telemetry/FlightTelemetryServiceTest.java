
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.config.FlightTelemetryProperties;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlightTelemetryServiceTest {

    private FlightTelemetryService service;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        FlightTelemetryProperties telemetryProperties =
                new FlightTelemetryProperties(
                        true,
                        1000L,
                        0.02,
                        100.0,
                        1000L,
                        0L
                );
        service = new FlightTelemetryService(telemetryProperties);
        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();
    }

    private FlightTelemetry initialize() {
        return service.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );
    }

    @Test
    void shouldInitializeFlightTelemetry() {
        FlightTelemetry telemetry = initialize();

        assertEquals(missionId, telemetry.missionId());
        assertEquals(executionId, telemetry.executionId());
        assertEquals(FlightStatus.READY, telemetry.flightStatus());
        assertEquals(100.0, telemetry.batteryPercent(), 0.001);
        assertEquals(0.0, telemetry.speedMps(), 0.001);
        assertNotNull(telemetry.timestamp());
    }

    @Test
    void shouldReturnLatestTelemetry() {
        FlightTelemetry telemetry = initialize();

        assertEquals(
                telemetry,
                service.getLatest(missionId).orElseThrow()
        );
    }

    @Test
    void shouldReturnEmptyForUnknownMission() {
        assertTrue(
                service.getLatest(UUID.randomUUID()).isEmpty()
        );
    }

    @Test
    void shouldUpdateFlightPosition() {
        initialize();

        FlightTelemetry telemetry = service.updatePosition(
                missionId,
                executionId,
                10.7505,
                106.6805,
                25.0,
                5.2,
                45.0,
                1,
                2
        );

        assertEquals(10.7505, telemetry.latitude(), 0.000001);
        assertEquals(106.6805, telemetry.longitude(), 0.000001);
        assertEquals(25.0, telemetry.altitudeM(), 0.001);
        assertEquals(5.2, telemetry.speedMps(), 0.001);
        assertEquals(45.0, telemetry.headingDeg(), 0.001);
        assertEquals(1, telemetry.currentWaypointSeq());
        assertEquals(2, telemetry.targetWaypointSeq());
        assertEquals(FlightStatus.FLYING, telemetry.flightStatus());
    }

    @Test
    void shouldUpdateCollectingStatusAndStopSpeed() {
        initialize();

        service.updatePosition(
                missionId, executionId,
                10.75, 106.68, 20.0,
                5.0, 90.0, 1, 2
        );

        FlightTelemetry telemetry = service.updateStatus(
                missionId,
                executionId,
                FlightStatus.COLLECTING
        );

        assertEquals(
                FlightStatus.COLLECTING,
                telemetry.flightStatus()
        );

        assertEquals(0.0, telemetry.speedMps(), 0.001);
    }

    @Test
    void shouldConsumeBattery() {
        initialize();

        FlightTelemetry telemetry = service.consumeBattery(
                missionId,
                executionId,
                2.5
        );

        assertEquals(97.5, telemetry.batteryPercent(), 0.001);
    }

    @Test
    void shouldNotAllowBatteryBelowZero() {
        initialize();

        FlightTelemetry telemetry = service.consumeBattery(
                missionId,
                executionId,
                150.0
        );

        assertEquals(0.0, telemetry.batteryPercent(), 0.001);
    }

    @Test
    void shouldRejectNegativeBatteryConsumption() {
        initialize();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.consumeBattery(
                        missionId,
                        executionId,
                        -5.0
                )
        );
    }

    @Test
    void shouldRejectDifferentExecutionId() {
        initialize();

        assertThrows(
                IllegalStateException.class,
                () -> service.updateStatus(
                        missionId,
                        UUID.randomUUID(),
                        FlightStatus.FLYING
                )
        );
    }

    @Test
    void shouldNotUpdatePositionAfterCompletion() {
        initialize();

        service.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.updatePosition(
                        missionId,
                        executionId,
                        10.75,
                        106.68,
                        20.0,
                        5.0,
                        90.0,
                        1,
                        2
                )
        );
    }

    @Test
    void shouldNotOverwriteTerminalStatus() {
        initialize();

        service.updateStatus(
                missionId,
                executionId,
                FlightStatus.CANCELLED
        );

        FlightTelemetry telemetry = service.updateStatus(
                missionId,
                executionId,
                FlightStatus.FLYING
        );

        assertEquals(
                FlightStatus.CANCELLED,
                telemetry.flightStatus()
        );
    }

    @Test
    void shouldNotResetExistingExecutionTelemetry() {
        initialize();

        service.consumeBattery(
                missionId,
                executionId,
                10.0
        );

        FlightTelemetry telemetry = initialize();

        assertEquals(90.0, telemetry.batteryPercent(), 0.001);
    }

    @Test
    void shouldManageMultipleMissionsIndependently() {
        initialize();

        UUID secondMissionId = UUID.randomUUID();
        UUID secondExecutionId = UUID.randomUUID();

        service.initialize(
                secondMissionId,
                secondExecutionId,
                11.0,
                107.0,
                30.0
        );

        service.consumeBattery(
                missionId,
                executionId,
                10.0
        );

        assertEquals(
                90.0,
                service.getLatest(missionId)
                        .orElseThrow()
                        .batteryPercent(),
                0.001
        );

        assertEquals(
                100.0,
                service.getLatest(secondMissionId)
                        .orElseThrow()
                        .batteryPercent(),
                0.001
        );
    }

    @Test
    void shouldRemoveTelemetry() {
        initialize();

        assertTrue(
                service.remove(missionId, executionId)
        );

        assertTrue(
                service.getLatest(missionId).isEmpty()
        );
    }

    @Test
    void shouldRejectUpdateBeforeInitialization() {
        assertThrows(
                IllegalStateException.class,
                () -> service.updateStatus(
                        missionId,
                        executionId,
                        FlightStatus.FLYING
                )
        );
    }

    @Test
    void shouldNormalizeHeading() {
        initialize();

        FlightTelemetry telemetry = service.updatePosition(
                missionId, executionId,
                10.75, 106.68, 20.0,
                5.0, -90.0, 1, 2
        );

        assertEquals(
                270.0,
                telemetry.headingDeg(),
                0.001
        );
    }

}
