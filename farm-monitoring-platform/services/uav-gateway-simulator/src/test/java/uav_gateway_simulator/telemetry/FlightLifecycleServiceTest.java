
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlightLifecycleServiceTest {

    private FlightTelemetryService telemetryService;
    private FlightLifecycleService lifecycleService;
    private RecoveryLeaseRegistry leaseRegistry;
    private FlightOwnershipService flightOwnershipService;

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

        telemetryService = new FlightTelemetryService(
                telemetryProperties
        );

        leaseRegistry = new RecoveryLeaseRegistry();

        flightOwnershipService = new FlightOwnershipService(
                leaseRegistry
        );

        lifecycleService = new FlightLifecycleService(
                telemetryService,
                flightOwnershipService,
                leaseRegistry
        );

        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        telemetryService.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );
    }

    private MissionResultResponse result(
            String status,
            int success,
            int failed
    ) {
        return new MissionResultResponse(
                missionId,
                executionId,
                status,
                success + failed,
                success,
                failed
        );
    }

    private FlightTelemetry latest() {
        return telemetryService.getLatest(missionId)
                .orElseThrow();
    }

    @Test
    void shouldMarkCompleted() {
        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("COMPLETED", 5, 0)
        );

        assertEquals(
                FlightStatus.COMPLETED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldMarkPartial() {
        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("PARTIAL", 3, 2)
        );

        assertEquals(
                FlightStatus.PARTIAL,
                latest().flightStatus()
        );
    }

    @Test
    void shouldMarkFailed() {
        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("FAILED", 0, 5)
        );

        assertEquals(
                FlightStatus.FAILED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldMarkCancelled() {
        lifecycleService.onCancelled(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.CANCELLED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldMarkDisconnected() {
        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.DISCONNECTED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldMarkRecovering() {
        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        lifecycleService.onRecovering(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.RECOVERING,
                latest().flightStatus()
        );
    }

    @Test
    void shouldRejectInvalidFinalStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleService.onMissionResult(
                        missionId,
                        executionId,
                        result("RUNNING", 0, 0)
                )
        );

        assertEquals(
                FlightStatus.READY,
                latest().flightStatus()
        );
    }

    @Test
    void shouldRejectMismatchedResultIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> lifecycleService.onMissionResult(
                        missionId,
                        UUID.randomUUID(),
                        result("COMPLETED", 5, 0)
                )
        );
    }

    @Test
    void shouldIgnoreDifferentExecution() {
        lifecycleService.onCancelled(
                missionId,
                UUID.randomUUID()
        );

        assertEquals(
                FlightStatus.READY,
                latest().flightStatus()
        );
    }

    @Test
    void shouldPreserveTerminalStatus() {
        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("COMPLETED", 5, 0)
        );

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        lifecycleService.onRecovering(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.COMPLETED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldIgnoreMissingTelemetry() {
        UUID unknownMissionId = UUID.randomUUID();

        assertDoesNotThrow(
                () -> lifecycleService.onCancelled(
                        unknownMissionId,
                        executionId
                )
        );

        assertTrue(
                telemetryService.getLatest(
                        unknownMissionId
                ).isEmpty()
        );
    }

    @Test
    void shouldStopSpeedWhenMissionCompleted() {
        telemetryService.updatePosition(
                missionId,
                executionId,
                10.751,
                106.681,
                25.0,
                5.0,
                90.0,
                1,
                2
        );

        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("COMPLETED", 5, 0)
        );

        assertEquals(
                0.0,
                latest().speedMps(),
                0.001
        );

        assertNull(latest().targetWaypointSeq());
    }

    @Test
    void shouldPreservePositionWhenDisconnected() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        FlightTelemetry before = latest();

        telemetryService.updatePosition(
                missionId,
                executionId,
                10.751,
                106.681,
                30.0,
                5.0,
                90.0,
                1,
                2
        );

        assertEquals(before, latest());

        assertEquals(
                FlightStatus.DISCONNECTED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldPreserveBatteryWhenDisconnected() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        FlightTelemetry before = latest();

        telemetryService.consumeBattery(
                missionId,
                executionId,
                5.0
        );

        assertEquals(before, latest());
    }

    @Test
    void shouldNotOverwriteDisconnectedWithActiveStatus() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        for (FlightStatus status : new FlightStatus[]{
                FlightStatus.READY,
                FlightStatus.FLYING,
                FlightStatus.COLLECTING
        }) {

            telemetryService.updateStatus(
                    missionId,
                    executionId,
                    status
            );

            assertEquals(
                    FlightStatus.DISCONNECTED,
                    latest().flightStatus()
            );

            telemetryService.updateStatusIfPresent(
                    missionId,
                    executionId,
                    status
            );

            assertEquals(
                    FlightStatus.DISCONNECTED,
                    latest().flightStatus()
            );
        }
    }

    @Test
    void shouldAllowMovementAfterRecovery() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        lifecycleService.onRecovering(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.RECOVERING,
                latest().flightStatus()
        );

        telemetryService.updatePosition(
                missionId,
                executionId,
                10.751,
                106.681,
                30.0,
                5.0,
                90.0,
                1,
                2
        );

        assertEquals(
                FlightStatus.FLYING,
                latest().flightStatus()
        );

        assertEquals(
                10.751,
                latest().latitude(),
                0.000001
        );
    }

    @Test
    void shouldAcceptConfirmedResultWhenDisconnected() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("COMPLETED", 5, 0)
        );

        assertEquals(
                FlightStatus.COMPLETED,
                latest().flightStatus()
        );

        assertEquals(
                0.0,
                latest().speedMps(),
                0.001
        );

        assertNull(latest().targetWaypointSeq());
    }

    @Test
    void shouldRecoverWhenHeartbeatReconnects() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        lifecycleService.onReconnected(
                missionId,
                executionId
        );

        assertEquals(
                FlightStatus.RECOVERING,
                latest().flightStatus()
        );
    }

    @Test
    void shouldNotChangeFlyingStatusOnReconnect() {

        telemetryService.updatePosition(
                missionId,
                executionId,
                10.751,
                106.681,
                25.0,
                5.0,
                90.0,
                1,
                2
        );

        FlightTelemetry before = latest();

        lifecycleService.onReconnected(
                missionId,
                executionId
        );

        assertEquals(before, latest());
    }

    @Test
    void shouldNotReconnectTerminalMission() {

        lifecycleService.onMissionResult(
                missionId,
                executionId,
                result("COMPLETED", 5, 0)
        );

        FlightTelemetry before = latest();

        lifecycleService.onReconnected(
                missionId,
                executionId
        );

        assertEquals(before, latest());

        assertEquals(
                FlightStatus.COMPLETED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldIgnoreReconnectFromDifferentExecution() {

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        FlightTelemetry before = latest();

        lifecycleService.onReconnected(
                missionId,
                UUID.randomUUID()
        );

        assertEquals(before, latest());

        assertEquals(
                FlightStatus.DISCONNECTED,
                latest().flightStatus()
        );
    }

    @Test
    void shouldPreserveTelemetryDataAfterReconnect() {

        telemetryService.updatePosition(
                missionId,
                executionId,
                10.751,
                106.681,
                25.0,
                5.0,
                90.0,
                1,
                2
        );

        telemetryService.consumeBattery(
                missionId,
                executionId,
                10.0
        );

        lifecycleService.onDisconnected(
                missionId,
                executionId
        );

        FlightTelemetry before = latest();

        lifecycleService.onReconnected(
                missionId,
                executionId
        );

        FlightTelemetry after = latest();

        assertEquals(before.latitude(), after.latitude());
        assertEquals(before.longitude(), after.longitude());
        assertEquals(before.altitudeM(), after.altitudeM());
        assertEquals(before.headingDeg(), after.headingDeg());

        assertEquals(
                before.batteryPercent(),
                after.batteryPercent()
        );

        assertEquals(
                before.currentWaypointSeq(),
                after.currentWaypointSeq()
        );

        assertEquals(
                before.targetWaypointSeq(),
                after.targetWaypointSeq()
        );

        assertEquals(
                0.0,
                after.speedMps(),
                0.001
        );

        assertEquals(
                FlightStatus.RECOVERING,
                after.flightStatus()
        );
    }
}
