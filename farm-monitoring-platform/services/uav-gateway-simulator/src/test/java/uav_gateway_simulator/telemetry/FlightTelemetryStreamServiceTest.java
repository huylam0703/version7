
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;
import uav_gateway_simulator.config.FlightTelemetryProperties;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlightTelemetryStreamServiceTest {

    private FlightTelemetryService telemetryService;
    private FlightTelemetryStreamService streamService;

    private UUID missionId;
    private UUID executionId;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        configure(true, 1000L, 0L);
    }

    private void configure(
            boolean enabled,
            long intervalMs,
            long timeoutMs
    ) {
        FlightTelemetryProperties properties =
                new FlightTelemetryProperties(
                        enabled,
                        1000L,
                        0.02,
                        100.0,
                        intervalMs,
                        timeoutMs
                );

        telemetryService =
                new FlightTelemetryService(properties);

        streamService =
                new FlightTelemetryStreamService(
                        telemetryService,
                        properties
                );
    }

    private void initializeTelemetry() {
        telemetryService.initialize(
                missionId,
                executionId,
                10.75,
                106.68,
                20.0
        );
    }

    @Test
    void shouldEmitInitialSnapshotImmediately() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .assertNext(event -> {
                    assertEquals("telemetry", event.event());
                    assertNotNull(event.data());
                    assertEquals(
                            missionId,
                            event.data().missionId()
                    );
                    assertEquals(
                            executionId,
                            event.data().executionId()
                    );
                    assertEquals(
                            FlightStatus.READY,
                            event.data().flightStatus()
                    );
                })
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldEmitUpdatedTelemetryAtConfiguredInterval() {
        configure(true, 500L, 0L);
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .assertNext(event ->
                        assertEquals(
                                100.0,
                                event.data().batteryPercent()
                        )
                )
                .then(() -> telemetryService.consumeBattery(
                        missionId,
                        executionId,
                        10.0
                ))
                .expectNoEvent(Duration.ofMillis(499))
                .thenAwait(Duration.ofMillis(1))
                .assertNext(event ->
                        assertEquals(
                                90.0,
                                event.data().batteryPercent()
                        )
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldRejectMissingTelemetry() {
        StepVerifier.create(streamService.stream(missionId))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(
                            ResponseStatusException.class,
                            error
                    );

                    ResponseStatusException exception =
                            (ResponseStatusException) error;

                    assertEquals(
                            HttpStatus.NOT_FOUND,
                            exception.getStatusCode()
                    );
                })
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldRejectDisabledTelemetry() {
        configure(false, 1000L, 0L);
        initializeTelemetry();

        StepVerifier.create(streamService.stream(missionId))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(
                            ResponseStatusException.class,
                            error
                    );

                    ResponseStatusException exception =
                            (ResponseStatusException) error;

                    assertEquals(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            exception.getStatusCode()
                    );
                })
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteAfterCompletedStatus() {
        verifyTerminalStatus(FlightStatus.COMPLETED);
    }

    @Test
    void shouldCompleteAfterPartialStatus() {
        verifyTerminalStatus(FlightStatus.PARTIAL);
    }

    @Test
    void shouldCompleteAfterFailedStatus() {
        verifyTerminalStatus(FlightStatus.FAILED);
    }

    @Test
    void shouldCompleteAfterCancelledStatus() {
        verifyTerminalStatus(FlightStatus.CANCELLED);
    }

    private void verifyTerminalStatus(FlightStatus status) {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.READY,
                                event.data().flightStatus()
                        )
                )
                .then(() -> telemetryService.updateStatus(
                        missionId,
                        executionId,
                        status
                ))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                status,
                                event.data().flightStatus()
                        )
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteImmediatelyWhenAlreadyTerminal() {
        initializeTelemetry();

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        StepVerifier.create(streamService.stream(missionId))
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.COMPLETED,
                                event.data().flightStatus()
                        )
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldKeepStreamingWhenDisconnected() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .then(() -> telemetryService.updateStatus(
                        missionId,
                        executionId,
                        FlightStatus.DISCONNECTED
                ))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.DISCONNECTED,
                                event.data().flightStatus()
                        )
                )
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.DISCONNECTED,
                                event.data().flightStatus()
                        )
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldKeepStreamingWhenRecovering() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .then(() -> telemetryService.updateStatus(
                        missionId,
                        executionId,
                        FlightStatus.RECOVERING
                ))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.RECOVERING,
                                event.data().flightStatus()
                        )
                )
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                FlightStatus.RECOVERING,
                                event.data().flightStatus()
                        )
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteWhenTimeoutReached() {
        configure(true, 1000L, 2500L);
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .thenAwait(Duration.ofSeconds(1))
                .expectNextCount(1)
                .thenAwait(Duration.ofSeconds(1))
                .expectNextCount(1)
                .thenAwait(Duration.ofMillis(500))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldKeepStreamingWhenTimeoutIsZero() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .thenAwait(Duration.ofSeconds(10))
                .expectNextCount(10)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteWhenExecutionChanges() {
        initializeTelemetry();

        UUID newExecutionId = UUID.randomUUID();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .then(() -> telemetryService.initialize(
                        missionId,
                        newExecutionId,
                        10.76,
                        106.69,
                        30.0
                ))
                .thenAwait(Duration.ofSeconds(1))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldAllowClientToCancelSubscription() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .thenAwait(Duration.ofSeconds(1))
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        assertTrue(
                telemetryService.getLatest(missionId)
                        .isPresent()
        );
    }

    @Test
    void shouldIsolateTelemetryBetweenMissions() {
        initializeTelemetry();

        UUID anotherMissionId = UUID.randomUUID();

        telemetryService.initialize(
                anotherMissionId,
                UUID.randomUUID(),
                11.0,
                107.0,
                50.0
        );

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .assertNext(event ->
                        assertEquals(
                                missionId,
                                event.data().missionId()
                        )
                )
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event ->
                        assertEquals(
                                missionId,
                                event.data().missionId()
                        )
                )
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }


    @Test
    void shouldNotSwitchExecutionBeforeSubscription() {
        initializeTelemetry();

        FlightTelemetry initial = telemetryService
                .getLatest(missionId)
                .orElseThrow();

        UUID newExecutionId = UUID.randomUUID();

        telemetryService.initialize(
                missionId,
                newExecutionId,
                10.76,
                106.69,
                30.0
        );

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(initial)
                )
                .assertNext(event -> {
                    assertNotNull(event.data());

                    assertEquals(
                            executionId,
                            event.data().executionId()
                    );
                })
                .thenAwait(Duration.ofSeconds(1))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldCompleteWhenTelemetryRemoved() {
        initializeTelemetry();

        StepVerifier.withVirtualTime(
                        () -> streamService.stream(missionId)
                )
                .expectNextCount(1)
                .then(() -> telemetryService.remove(
                        missionId,
                        executionId
                ))
                .thenAwait(Duration.ofSeconds(1))
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

}
