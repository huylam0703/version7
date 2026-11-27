
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import uav_gateway_simulator.config.FlightTelemetryProperties;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlightTelemetryStreamService {

    private final FlightTelemetryService telemetryService;
    private final FlightTelemetryProperties telemetryProperties;

    public Flux<ServerSentEvent<FlightTelemetry>> stream(
            UUID missionId
    ) {
        return Flux.defer(() -> {
            validateEnabled();

            FlightTelemetry initial = telemetryService
                    .getLatest(missionId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Flight telemetry is not available"
                    ));

            return stream(initial);
        });
    }

    public Flux<ServerSentEvent<FlightTelemetry>> stream(
            FlightTelemetry initial
    ) {
        Objects.requireNonNull(initial, "Initial telemetry is required");

        return Flux.defer(() -> {
            validateEnabled();

            UUID missionId = initial.missionId();
            UUID executionId = initial.executionId();

            Duration interval = Duration.ofMillis(
                    telemetryProperties.ssePublishIntervalMs()
            );

            Flux<FlightTelemetry> updates = Flux.interval(interval)
                    .onBackpressureDrop()
                    .map(tick -> telemetryService.getLatest(missionId))
                    .takeWhile(snapshot -> snapshot.isPresent())
                    .map(snapshot -> snapshot.orElseThrow())
                    .takeWhile(snapshot ->
                            executionId.equals(snapshot.executionId())
                    );

            Flux<ServerSentEvent<FlightTelemetry>> events =
                    Flux.concat(Flux.just(initial), updates)
                            .takeUntil(snapshot ->
                                    isTerminal(snapshot.flightStatus())
                            )
                            .map(this::toEvent);

            long timeoutMs = telemetryProperties.sseTimeoutMs();

            if (timeoutMs > 0) {
                events = events.take(
                        Duration.ofMillis(timeoutMs)
                );
            }

            return events;
        });
    }

    private void validateEnabled() {
        if (!telemetryProperties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Flight telemetry is disabled"
            );
        }
    }

    private ServerSentEvent<FlightTelemetry> toEvent(
            FlightTelemetry telemetry
    ) {
        return ServerSentEvent.<FlightTelemetry>builder()
                .event("telemetry")
                .data(telemetry)
                .build();
    }

    private boolean isTerminal(FlightStatus status) {
        return switch (status) {
            case COMPLETED, PARTIAL, FAILED, CANCELLED -> true;
            default -> false;
        };
    }
}
