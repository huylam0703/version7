
package uav_gateway_simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.telemetry.FlightTelemetry;
import uav_gateway_simulator.telemetry.FlightTelemetryService;
import uav_gateway_simulator.telemetry.FlightTelemetryStreamService;
import uav_gateway_simulator.telemetry.TelemetryAuthorizationService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uav/missions")
@RequiredArgsConstructor
public class FlightTelemetryController {

    private final FlightTelemetryService telemetryService;
    private final FlightTelemetryProperties telemetryProperties;
    private final FlightTelemetryStreamService streamService;
    private final TelemetryAuthorizationService authorizationService;

    @GetMapping("/{missionId}/telemetry")
    public Mono<ResponseEntity<FlightTelemetry>> getTelemetry(
            @PathVariable("missionId") UUID missionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return authorizationService.authorize(missionId, jwt)
                .then(Mono.fromSupplier(() -> {

                    validateTokenExpiration(jwt);
                    validateEnabled();

                    FlightTelemetry telemetry =
                            getRequiredTelemetry(missionId);

                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore())
                            .body(telemetry);
                }));
    }

    @GetMapping(
            value = "/{missionId}/telemetry/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Mono<ResponseEntity<
            Flux<ServerSentEvent<FlightTelemetry>>>>
    streamTelemetry(
            @PathVariable("missionId") UUID missionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return authorizationService.authorize(missionId, jwt)
                .then(Mono.fromSupplier(() -> {

                    validateTokenExpiration(jwt);
                    validateEnabled();

                    FlightTelemetry initial =
                            getRequiredTelemetry(missionId);

                    Flux<ServerSentEvent<FlightTelemetry>> events =
                            Flux.defer(() -> {

                                Duration remaining =
                                        Duration.between(
                                                Instant.now(),
                                                jwt.getExpiresAt()
                                        );

                                if (remaining.isZero()
                                        || remaining.isNegative()) {
                                    return Flux.empty();
                                }

                                return streamService.stream(initial)
                                        .take(remaining)
                                        .takeWhile(event ->
                                                Instant.now().isBefore(
                                                        jwt.getExpiresAt()
                                                )
                                        );
                            });

                    return ResponseEntity.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .cacheControl(CacheControl.noStore())
                            .header("X-Accel-Buffering", "no")
                            .body(events);
                }));
    }

    private void validateEnabled() {
        if (!telemetryProperties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Flight telemetry is disabled"
            );
        }
    }

    private FlightTelemetry getRequiredTelemetry(
            UUID missionId
    ) {
        return telemetryService.getLatest(missionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Flight telemetry is not available"
                ));
    }

    private void validateTokenExpiration(Jwt jwt) {
        if (jwt == null
                || jwt.getExpiresAt() == null
                || !Instant.now().isBefore(jwt.getExpiresAt())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Token is missing or expired"
            );
        }
    }
}
