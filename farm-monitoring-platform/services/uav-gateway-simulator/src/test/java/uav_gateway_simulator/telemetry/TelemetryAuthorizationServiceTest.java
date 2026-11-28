
package uav_gateway_simulator.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import uav_gateway_simulator.client.TelemetryMissionClient;
import uav_gateway_simulator.dto.response.MissionDetailResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelemetryAuthorizationServiceTest {

    private TelemetryMissionClient missionClient;
    private TelemetryAuthorizationService authorizationService;

    private UUID missionId;
    private String userId;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        userId = UUID.randomUUID().toString();

        missionClient = mock(TelemetryMissionClient.class);

        authorizationService =
                new TelemetryAuthorizationService(missionClient);
    }

    private Jwt createJwt(String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(userId)
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private MissionDetailResponse mockMission(
            UUID id,
            String createdBy
    ) {
        MissionDetailResponse mission =
                mock(MissionDetailResponse.class);

        when(mission.missionId()).thenReturn(id);
        when(mission.createdBy()).thenReturn(createdBy);

        return mission;
    }

    private void stubMission(MissionDetailResponse mission) {
        when(missionClient.getMissionDetail(
                missionId,
                "test-token"
        )).thenReturn(Mono.just(mission));
    }

    private void expectStatus(
            Mono<Void> result,
            HttpStatus status
    ) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(
                            ResponseStatusException.class,
                            error
                    );

                    ResponseStatusException exception =
                            (ResponseStatusException) error;

                    assertEquals(
                            status,
                            exception.getStatusCode()
                    );
                })
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldAllowAdminToAccessAnyMission() {
        stubMission(mockMission(
                missionId,
                UUID.randomUUID().toString()
        ));

        StepVerifier.create(
                        authorizationService.authorize(
                                missionId,
                                createJwt("ADMIN")
                        )
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldAllowOperatorToAccessOwnMission() {
        stubMission(mockMission(missionId, userId));

        StepVerifier.create(
                        authorizationService.authorize(
                                missionId,
                                createJwt("OPERATOR")
                        )
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldRejectOperatorAccessingAnotherMission() {
        stubMission(mockMission(
                missionId,
                UUID.randomUUID().toString()
        ));

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("OPERATOR")
                ),
                HttpStatus.FORBIDDEN
        );
    }

    @Test
    void shouldRejectServiceRole() {
        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("SERVICE")
                ),
                HttpStatus.FORBIDDEN
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectUnknownRole() {
        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("UNKNOWN")
                ),
                HttpStatus.FORBIDDEN
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectMissingAuthentication() {
        expectStatus(
                authorizationService.authorize(
                        missionId,
                        null
                ),
                HttpStatus.UNAUTHORIZED
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectClientCredentialsToken() {
        Jwt clientJwt = Jwt.withTokenValue("client-token")
                .header("alg", "RS256")
                .subject("uav-simulator")
                .claim("scope", "mission:read")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        clientJwt
                ),
                HttpStatus.FORBIDDEN
        );

        verifyNoInteractions(missionClient);
    }

    @Test
    void shouldRejectMismatchedMissionId() {
        stubMission(mockMission(
                UUID.randomUUID(),
                userId
        ));

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("ADMIN")
                ),
                HttpStatus.FORBIDDEN
        );
    }

    @Test
    void shouldRejectEmptyMissionResponse() {
        when(missionClient.getMissionDetail(
                missionId,
                "test-token"
        )).thenReturn(Mono.empty());

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("ADMIN")
                ),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    void shouldHandleMissionServiceTimeout() {
        when(missionClient.getMissionDetail(
                missionId,
                "test-token"
        )).thenReturn(Mono.error(new TimeoutException()));

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("ADMIN")
                ),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    void shouldHandleMissionServiceUnauthorized() {
        stubHttpError(401);

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("ADMIN")
                ),
                HttpStatus.UNAUTHORIZED
        );
    }

    @Test
    void shouldHandleMissionServiceForbidden() {
        stubHttpError(403);

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("OPERATOR")
                ),
                HttpStatus.FORBIDDEN
        );
    }

    @Test
    void shouldHandleMissionNotFound() {
        stubHttpError(404);

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("OPERATOR")
                ),
                HttpStatus.FORBIDDEN
        );
    }

    @Test
    void shouldHandleMissionServiceUnavailable() {
        stubHttpError(500);

        expectStatus(
                authorizationService.authorize(
                        missionId,
                        createJwt("ADMIN")
                ),
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    @Test
    void shouldForwardOriginalUserToken() {
        stubMission(mockMission(missionId, userId));

        StepVerifier.create(
                        authorizationService.authorize(
                                missionId,
                                createJwt("OPERATOR")
                        )
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(missionClient).getMissionDetail(
                missionId,
                "test-token"
        );
    }

    private void stubHttpError(int status) {
        WebClientResponseException exception =
                WebClientResponseException.create(
                        status,
                        "Mission request failed",
                        null,
                        new byte[0],
                        null
                );

        when(missionClient.getMissionDetail(
                missionId,
                "test-token"
        )).thenReturn(Mono.error(exception));
    }
}
