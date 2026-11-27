package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import uav_gateway_simulator.dto.response.MissionRecoveryClaimResponse;
import java.time.Instant;
import java.util.concurrent.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FlightMovementServiceTest {

    private FlightTelemetryService telemetryService;
    private FlightMovementService movementService;
    private RecoveryLeaseRegistry registry;

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

        this.telemetryService =
                new FlightTelemetryService(telemetryProperties);

        this.registry = new RecoveryLeaseRegistry();

        FlightOwnershipService ownershipService =
                new FlightOwnershipService(registry);

        this.movementService = new FlightMovementService(
                new FlightPositionCalculator(),
                telemetryService,
                ownershipService,
                telemetryProperties
        );

        this.missionId = UUID.randomUUID();
        this.executionId = UUID.randomUUID();
    }


    private WaypointResponse waypoint(
            int seq,
            String latitude,
            String longitude,
            String altitude
    ) {
        return new WaypointResponse(
                UUID.randomUUID(),
                seq,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                new BigDecimal(altitude),
                "PENDING",
                List.of()
        );
    }

    @Test
    void shouldMoveToTargetWaypoint() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        movementService.move(
                missionId,
                executionId,
                from,
                target,
                10
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(
                10.751,
                telemetry.latitude(),
                0.000001
        );

        assertEquals(
                106.681,
                telemetry.longitude(),
                0.000001
        );

        assertEquals(
                30.0,
                telemetry.altitudeM(),
                0.001
        );
    }

    @Test
    void shouldSetCollectingStatusAfterArrival() {
        WaypointResponse target =
                waypoint(1, "10.75", "106.68", "20");

        movementService.move(
                missionId,
                executionId,
                null,
                target,
                0
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(
                FlightStatus.COLLECTING,
                telemetry.flightStatus()
        );
    }

    @Test
    void shouldStopSpeedAfterArrival() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        movementService.move(
                missionId,
                executionId,
                from,
                target,
                10
        );

        assertEquals(
                0.0,
                telemetryService.getLatest(missionId)
                        .orElseThrow()
                        .speedMps(),
                0.001
        );
    }

    @Test
    void shouldUpdateCurrentWaypointSequence() {
        WaypointResponse target =
                waypoint(3, "10.75", "106.68", "20");

        movementService.move(
                missionId,
                executionId,
                null,
                target,
                0
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(3, telemetry.currentWaypointSeq());
        assertNull(telemetry.targetWaypointSeq());
    }

    @Test
    void shouldConsumeBatteryDuringMovement() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        movementService.move(
                missionId,
                executionId,
                from,
                target,
                100
        );

        assertTrue(
                telemetryService.getLatest(missionId)
                        .orElseThrow()
                        .batteryPercent() < 100.0
        );
    }

    @Test
    void shouldRejectNegativeDuration() {
        WaypointResponse target =
                waypoint(1, "10.75", "106.68", "20");

        assertThrows(
                IllegalArgumentException.class,
                () -> movementService.move(
                        missionId,
                        executionId,
                        null,
                        target,
                        -1
                )
        );
    }

    @Test
    void shouldRejectNullTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> movementService.move(
                        missionId,
                        executionId,
                        null,
                        null,
                        100
                )
        );
    }

    @Test
    void shouldPreserveBatteryAcrossWaypoints() {
        WaypointResponse wp1 =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse wp2 =
                waypoint(2, "10.751", "106.681", "25");

        WaypointResponse wp3 =
                waypoint(3, "10.752", "106.682", "30");

        movementService.move(
                missionId, executionId,
                wp1, wp2, 100
        );

        double batteryAfterWp2 =
                telemetryService.getLatest(missionId)
                        .orElseThrow()
                        .batteryPercent();

        movementService.move(
                missionId, executionId,
                wp2, wp3, 100
        );

        double batteryAfterWp3 =
                telemetryService.getLatest(missionId)
                        .orElseThrow()
                        .batteryPercent();

        assertTrue(batteryAfterWp3 < batteryAfterWp2);
    }

    @Test
    void shouldStopMovementWhenMissionIsCancelled() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        AtomicInteger checks = new AtomicInteger();

        assertThrows(
                MissionCancelledException.class,
                () -> movementService.move(
                        missionId,
                        executionId,
                        from,
                        target,
                        100,
                        () -> {
                            if (checks.incrementAndGet() >= 2) {
                                throw new MissionCancelledException(
                                        "Mission cancelled during flight"
                                );
                            }
                        }
                )
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(1, telemetry.currentWaypointSeq());
        assertEquals(2, telemetry.targetWaypointSeq());

        assertNotEquals(
                FlightStatus.COLLECTING,
                telemetry.flightStatus()
        );
    }

    @Test
    void shouldStopMovementWhenRecoveryLeaseIsLost() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        AtomicInteger checks = new AtomicInteger();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> movementService.move(
                        missionId,
                        executionId,
                        from,
                        target,
                        100,
                        () -> {
                            if (checks.incrementAndGet() >= 2) {
                                throw new IllegalStateException(
                                        "Recovery ownership lost"
                                );
                            }
                        }
                )
        );

        assertEquals(
                "Recovery ownership lost",
                exception.getMessage()
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(1, telemetry.currentWaypointSeq());

        assertNotEquals(
                FlightStatus.COLLECTING,
                telemetry.flightStatus()
        );
    }

    @Test
    void shouldUpdateIntermediateFlightPosition() {
        WaypointResponse from =
                waypoint(1, "10.75", "106.68", "20");

        WaypointResponse target =
                waypoint(2, "10.751", "106.681", "30");

        AtomicInteger checks = new AtomicInteger();

        movementService.move(
                missionId,
                executionId,
                from,
                target,
                3000,
                () -> {
                    if (checks.incrementAndGet() == 3) {

                        FlightTelemetry telemetry =
                                telemetryService.getLatest(missionId)
                                        .orElseThrow();

                        assertTrue(telemetry.latitude() > 10.75);
                        assertTrue(telemetry.latitude() < 10.751);

                        assertTrue(telemetry.longitude() > 106.68);
                        assertTrue(telemetry.longitude() < 106.681);

                        assertTrue(telemetry.altitudeM() > 20.0);
                        assertTrue(telemetry.altitudeM() < 30.0);

                        assertEquals(
                                FlightStatus.FLYING,
                                telemetry.flightStatus()
                        );
                    }
                }
        );

        assertTrue(checks.get() >= 3);

        assertEquals(
                FlightStatus.COLLECTING,
                telemetryService.getLatest(missionId)
                        .orElseThrow()
                        .flightStatus()
        );
    }

    @Test
    void shouldRejectMovementBeforeInitialization() {
        WaypointResponse target =
                waypoint(1, "10.75", "106.68", "20");

        assertThrows(
                IllegalStateException.class,
                () -> movementService.move(
                        missionId,
                        executionId,
                        null,
                        target,
                        100,
                        () -> {
                            throw new IllegalStateException(
                                    "Execution is not allowed"
                            );
                        }
                )
        );

        assertTrue(
                telemetryService.getLatest(missionId).isEmpty()
        );
    }

    @Test
    void shouldRestoreFromLastConfirmedWaypoint() {

        WaypointResponse previous =
                waypoint(3, "10.75", "106.68", "20");

        WaypointResponse next =
                waypoint(4, "10.751", "106.681", "30");

        movementService.restoreForRecovery(
                missionId,
                executionId,
                previous,
                next
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(
                FlightStatus.RECOVERING,
                telemetry.flightStatus()
        );

        assertEquals(3, telemetry.currentWaypointSeq());
        assertEquals(4, telemetry.targetWaypointSeq());

        assertEquals(10.75, telemetry.latitude(), 0.000001);
        assertEquals(106.68, telemetry.longitude(), 0.000001);
        assertEquals(20.0, telemetry.altitudeM(), 0.001);
        assertEquals(0.0, telemetry.speedMps(), 0.001);
    }

    @Test
    void shouldRestoreBeforeFirstWaypoint() {

        WaypointResponse first =
                waypoint(1, "10.75", "106.68", "20");

        movementService.restoreForRecovery(
                missionId,
                executionId,
                null,
                first
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertNull(telemetry.currentWaypointSeq());
        assertEquals(1, telemetry.targetWaypointSeq());

        assertEquals(
                FlightStatus.RECOVERING,
                telemetry.flightStatus()
        );
    }

    @Test
    void shouldRestoreWhenAllWaypointsReached() {

        WaypointResponse last =
                waypoint(5, "10.75", "106.68", "20");

        movementService.restoreForRecovery(
                missionId,
                executionId,
                last,
                null
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(5, telemetry.currentWaypointSeq());
        assertNull(telemetry.targetWaypointSeq());

        assertEquals(
                FlightStatus.RECOVERING,
                telemetry.flightStatus()
        );

        assertEquals(0.0, telemetry.speedMps(), 0.001);
    }

    @Test
    void shouldPreserveBatteryDuringRecovery() {

        telemetryService.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );

        telemetryService.consumeBattery(
                missionId,
                executionId,
                25.0
        );

        movementService.restoreForRecovery(
                missionId,
                executionId,
                waypoint(3, "10.75", "106.68", "20"),
                waypoint(4, "10.751", "106.681", "30")
        );

        FlightTelemetry telemetry =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        assertEquals(
                75.0,
                telemetry.batteryPercent(),
                0.001
        );
    }

    @Test
    void shouldNotOverwriteCompletedMissionDuringRecovery() {

        telemetryService.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        FlightTelemetry before =
                telemetryService.getLatest(missionId)
                        .orElseThrow();

        movementService.restoreForRecovery(
                missionId,
                executionId,
                waypoint(5, "10.75", "106.68", "20"),
                null
        );

        assertEquals(
                before,
                telemetryService.getLatest(missionId)
                        .orElseThrow()
        );
    }

    @Test
    void shouldRejectRecoveryFromDifferentExecution() {

        telemetryService.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );

        assertThrows(
                IllegalStateException.class,
                () -> movementService.restoreForRecovery(
                        missionId,
                        UUID.randomUUID(),
                        waypoint(3, "10.75", "106.68", "20"),
                        waypoint(4, "10.751", "106.681", "30")
                )
        );
    }

    private FlightOwnershipToken lease(UUID instance, long generation, Instant until) {
        registry.register(new MissionRecoveryClaimResponse(
                missionId, executionId, instance, generation, until));
        return new FlightOwnershipToken(executionId, instance, generation);
    }

    private void assertMovementFenced(int scenario) throws Exception {
        UUID instance = UUID.randomUUID();
        FlightOwnershipToken old = lease(instance, 1L, Instant.now().plusSeconds(120));
        WaypointResponse from = waypoint(1, "10.75", "106.68", "20");
        WaypointResponse target = waypoint(2, "10.751", "106.681", "30");
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> work = worker.submit(() -> movementService.move(
                    missionId, executionId, from, target, scenario == 3 ? 0 : 100,
                    () -> {
                        if (checks.incrementAndGet() == 2) {
                            paused.countDown();
                            try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("Movement release timed out");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(e);
                            }
                        }
                    }, old));
            assertTrue(paused.await(5, TimeUnit.SECONDS));
            if (scenario == 0 || scenario == 3) {
                FlightOwnershipToken next = lease(instance, 2L, Instant.now().plusSeconds(120));
                movementService.restoreForRecovery(missionId, executionId,
                        waypoint(3, "11.0", "107.0", "40"), target, next);
            } else if (scenario == 1) {
                assertTrue(registry.markLostIfCurrent(old));
            } else {
                lease(instance, 1L, Instant.now().minusSeconds(10));
            }
            FlightTelemetry before = telemetryService.getLatest(missionId).orElseThrow();
            release.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> work.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(before, telemetryService.getLatest(missionId).orElseThrow());
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldFenceOldMovementAfterRecoveryRestore() throws Exception {
        assertMovementFenced(0);
    }

    @Test
    void shouldFenceBatteryAndPositionAfterOwnershipLost() throws Exception {
        assertMovementFenced(1);
    }

    @Test
    void shouldFenceMovementAfterLeaseExpires() throws Exception {
        assertMovementFenced(2);
    }

    @Test
    void shouldFenceFinalArrivalAfterGenerationChanges() throws Exception {
        assertMovementFenced(3);
    }

    @Test
    void shouldRejectStaleRecoveryRestore() {
        UUID instance = UUID.randomUUID();
        FlightOwnershipToken old = lease(instance, 1L, Instant.now().plusSeconds(120));
        FlightOwnershipToken next = lease(instance, 2L, Instant.now().plusSeconds(120));
        movementService.restoreForRecovery(missionId, executionId,
                waypoint(3, "11.0", "107.0", "40"), null, next);
        FlightTelemetry before = telemetryService.getLatest(missionId).orElseThrow();
        assertThrows(IllegalStateException.class, () -> movementService.restoreForRecovery(
                missionId, executionId, waypoint(1, "10.75", "106.68", "20"), null, old));
        assertEquals(before, telemetryService.getLatest(missionId).orElseThrow());
    }

    @Test
    void shouldPreserveTerminalTelemetryAgainstOldWorker() {
        UUID instance = UUID.randomUUID();
        FlightOwnershipToken old = lease(instance, 1L, Instant.now().plusSeconds(120));
        FlightOwnershipToken next = lease(instance, 2L, Instant.now().plusSeconds(120));
        WaypointResponse target = waypoint(2, "10.751", "106.681", "30");
        movementService.restoreForRecovery(missionId, executionId, target, null, next);
        new FlightOwnershipService(registry).executeIfCurrent(next,
                () -> telemetryService.updateStatus(missionId, executionId, FlightStatus.COMPLETED));
        FlightTelemetry before = telemetryService.getLatest(missionId).orElseThrow();
        assertThrows(IllegalStateException.class, () -> movementService.move(
                missionId, executionId, null, target, 0, () -> {}, old));
        assertEquals(before, telemetryService.getLatest(missionId).orElseThrow());
    }
}
