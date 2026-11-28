
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import uav_gateway_simulator.client.TelemetryMissionClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelemetryAuthorizationService {

    private final TelemetryMissionClient missionClient;

    public Mono<Void> authorize(
            UUID missionId,
            Jwt jwt
    ) {
        return Mono.defer(() -> {

            if (jwt == null) {
                return Mono.error(
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Authentication is required"
                        )
                );
            }

            String role = jwt.getClaimAsString("role");
            String userId = jwt.getSubject();

            boolean admin = "ADMIN".equals(role);
            boolean operator = "OPERATOR".equals(role);

            if ((!admin && !operator)
                    || userId == null
                    || userId.isBlank()) {
                return Mono.error(accessDenied());
            }

            return missionClient.getMissionDetail(
                            missionId,
                            jwt.getTokenValue()
                    )
                    .onErrorMap(
                            WebClientResponseException.class,
                            this::mapHttpError
                    )
                    .onErrorMap(
                            error -> !(error instanceof
                                    ResponseStatusException),
                            error -> new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    "Mission authorization unavailable",
                                    error
                            )
                    )
                    .switchIfEmpty(Mono.error(
                            new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE,
                                    "Mission detail is unavailable"
                            )
                    ))
                    .flatMap(mission -> {

                        if (!missionId.equals(
                                mission.missionId()
                        )) {
                            return Mono.error(accessDenied());
                        }

                        if (admin) {
                            return Mono.<Void>empty();
                        }

                        if (userId.equals(mission.createdBy())) {
                            return Mono.<Void>empty();
                        }

                        return Mono.error(accessDenied());
                    });
        });
    }

    private ResponseStatusException accessDenied() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Mission access denied"
        );
    }

    private ResponseStatusException mapHttpError(
            WebClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();

        return switch (status) {
            case 401 -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication"
            );

            case 403, 404 -> accessDenied();

            default -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Mission authorization unavailable"
            );
        };
    }
}
