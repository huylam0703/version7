
package uav_gateway_simulator.controller;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import uav_gateway_simulator.telemetry.FlightStatus;
import uav_gateway_simulator.telemetry.FlightTelemetry;
import uav_gateway_simulator.telemetry.FlightTelemetryService;
import uav_gateway_simulator.telemetry.TelemetryAuthorizationService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.reactive.server
        .SecurityMockServerConfigurers.springSecurity;

class FlightTelemetryJwtSecurityTest {

    private static final String ISSUER = "farm-auth";
    private static final String AUDIENCE = "farm-api";

    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    private static final String REST_URL =
            "/api/v1/uav/missions/"
                    + MISSION_ID
                    + "/telemetry";

    private static final String SSE_URL =
            REST_URL + "/stream";

    private static final KeyPair VALID_KEYS = generateKeys();
    private static final KeyPair INVALID_KEYS = generateKeys();

    private AnnotationConfigApplicationContext context;
    private WebTestClient webTestClient;

    private FlightTelemetryService telemetryService;
    private TelemetryAuthorizationService authorizationService;

    @Configuration
    @Import(FlightTelemetrySecurityTest.TestConfig.class)
    static class JwtTestConfig {

        @Bean
        @Primary
        ReactiveJwtDecoder realJwtDecoder() {

            NimbusReactiveJwtDecoder decoder =
                    NimbusReactiveJwtDecoder
                            .withPublicKey(
                                    (RSAPublicKey)
                                            VALID_KEYS.getPublic()
                            )
                            .build();

            OAuth2TokenValidator<Jwt> issuerValidator =
                    JwtValidators.createDefaultWithIssuer(
                            ISSUER
                    );

            OAuth2TokenValidator<Jwt> audienceValidator =
                    jwt -> jwt.getAudience() != null
                            && jwt.getAudience().contains(AUDIENCE)
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error(
                                    "invalid_token",
                                    "Invalid audience",
                                    null
                            )
                    );

            decoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<>(
                            issuerValidator,
                            audienceValidator
                    )
            );

            return decoder;
        }
    }

    @BeforeEach
    void setUp() {

        context = new AnnotationConfigApplicationContext(
                JwtTestConfig.class
        );

        telemetryService = context.getBean(
                FlightTelemetryService.class
        );

        authorizationService = context.getBean(
                TelemetryAuthorizationService.class
        );

        telemetryService.initialize(
                MISSION_ID,
                EXECUTION_ID,
                10.75,
                106.68,
                20.0
        );

        webTestClient = WebTestClient
                .bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("RSA");

            generator.initialize(2048);

            return generator.generateKeyPair();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot generate RSA keys",
                    exception
            );
        }
    }

    private String createToken(
            String role,
            String issuer,
            String audience,
            Instant notBefore,
            Instant expiresAt,
            KeyPair keys
    ) throws JOSEException {

        JWTClaimsSet.Builder claims =
                new JWTClaimsSet.Builder()
                        .subject(UUID.randomUUID().toString())
                        .issuer(issuer)
                        .audience(audience)
                        .issueTime(
                                Date.from(
                                        Instant.now().minusSeconds(300)
                                )
                        )
                        .notBeforeTime(Date.from(notBefore))
                        .expirationTime(Date.from(expiresAt))
                        .jwtID(UUID.randomUUID().toString());

        if (role != null) {
            claims.claim("role", role);
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims.build()
        );

        signedJWT.sign(
                new RSASSASigner(
                        (RSAPrivateKey) keys.getPrivate()
                )
        );

        return signedJWT.serialize();
    }

    private String validToken(String role)
            throws JOSEException {

        return createToken(
                role,
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600),
                VALID_KEYS
        );
    }

    private WebTestClient.ResponseSpec request(
            String url,
            String token
    ) {
        return webTestClient.get()
                .uri(url)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .exchange();
    }

    // =====================================
    // JWT SIGNATURE AND CLAIM VALIDATION
    // =====================================

    @Test
    void shouldAcceptValidAdminJwt() throws Exception {

        request(REST_URL, validToken("ADMIN"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.missionId")
                .isEqualTo(MISSION_ID.toString());
    }

    @Test
    void shouldAcceptValidOperatorJwt() throws Exception {

        request(REST_URL, validToken("OPERATOR"))
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600),
                INVALID_KEYS
        );

        request(REST_URL, token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectInvalidIssuer() throws Exception {

        String token = createToken(
                "ADMIN",
                "invalid-issuer",
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600),
                VALID_KEYS
        );

        request(REST_URL, token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectInvalidAudience() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                "invalid-audience",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600),
                VALID_KEYS
        );

        request(REST_URL, token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectExpiredJwt() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(240),
                Instant.now().minusSeconds(120),
                VALID_KEYS
        );

        request(REST_URL, token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectJwtNotYetValid() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().plusSeconds(120),
                Instant.now().plusSeconds(3600),
                VALID_KEYS
        );

        request(REST_URL, token)
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectMalformedJwt() {

        request(REST_URL, "invalid.jwt.token")
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectJwtWithoutRole() throws Exception {

        request(REST_URL, validToken(null))
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectServiceRoleJwt() throws Exception {

        request(REST_URL, validToken("SERVICE"))
                .expectStatus().isForbidden();
    }

    // =====================================
    // SSE TOKEN EXPIRATION
    // =====================================

    @Test
    void shouldRejectExpiredJwtForSse() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(240),
                Instant.now().minusSeconds(120),
                VALID_KEYS
        );

        webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldCloseSseWhenJwtExpires() throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(5),
                VALID_KEYS
        );

        var response = webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(
                        new ParameterizedTypeReference<
                                ServerSentEvent<FlightTelemetry>>() {}
                );

        StepVerifier.create(
                        response.getResponseBody()
                                .doOnNext(event -> {
                                    assertNotNull(event.data());
                                    assertEquals(
                                            MISSION_ID,
                                            event.data().missionId()
                                    );
                                    assertEquals(
                                            FlightStatus.READY,
                                            event.data().flightStatus()
                                    );
                                })
                )
                .expectNextCount(1)
                .thenConsumeWhile(event -> true)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void shouldAllowSseWithValidJwt() throws Exception {

        telemetryService.updateStatus(
                MISSION_ID,
                EXECUTION_ID,
                FlightStatus.COMPLETED
        );

        var response = webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(
                                getValidAdminToken()
                        )
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(
                        new ParameterizedTypeReference<
                                ServerSentEvent<FlightTelemetry>>() {}
                );

        StepVerifier.create(response.getResponseBody())
                .assertNext(event -> {
                    assertNotNull(event.data());
                    assertEquals(
                            FlightStatus.COMPLETED,
                            event.data().flightStatus()
                    );
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    private String getValidAdminToken() {
        try {
            return validToken("ADMIN");
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void shouldRejectInvalidSignatureForSse()
            throws Exception {

        String token = createToken(
                "ADMIN",
                ISSUER,
                AUDIENCE,
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(3600),
                INVALID_KEYS
        );

        webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // =====================================
    // MISSION AUTHORIZATION ENFORCEMENT
    // =====================================

    @Test
    void shouldRejectRestWhenMissionAccessDenied()
            throws Exception {

        when(authorizationService.authorize(
                eq(MISSION_ID),
                any(Jwt.class)
        )).thenReturn(
                Mono.error(
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Mission access denied"
                        )
                )
        );

        request(REST_URL, validToken("OPERATOR"))
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectSseWhenMissionAccessDenied()
            throws Exception {

        when(authorizationService.authorize(
                eq(MISSION_ID),
                any(Jwt.class)
        )).thenReturn(
                Mono.error(
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Mission access denied"
                        )
                )
        );

        String token = validToken("OPERATOR");

        webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectRestWhenAuthorizationUnavailable()
            throws Exception {

        when(authorizationService.authorize(
                eq(MISSION_ID),
                any(Jwt.class)
        )).thenReturn(
                Mono.error(
                        new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Authorization unavailable"
                        )
                )
        );

        request(REST_URL, validToken("ADMIN"))
                .expectStatus().isEqualTo(503);
    }

    @Test
    void shouldRejectSseWhenAuthorizationUnavailable()
            throws Exception {

        when(authorizationService.authorize(
                eq(MISSION_ID),
                any(Jwt.class)
        )).thenReturn(
                Mono.error(
                        new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Authorization unavailable"
                        )
                )
        );

        String token = validToken("ADMIN");

        webTestClient.get()
                .uri(SSE_URL)
                .headers(headers ->
                        headers.setBearerAuth(token)
                )
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void shouldNotAuthorizeRequestWithInvalidJwt() {

        clearInvocations(authorizationService);

        request(REST_URL, "invalid.jwt.token")
                .expectStatus().isUnauthorized();

        verifyNoInteractions(authorizationService);
    }

    @Test
    void shouldNotAuthorizeRequestWithoutRequiredRole()
            throws Exception {

        clearInvocations(authorizationService);

        request(REST_URL, validToken("SERVICE"))
                .expectStatus().isForbidden();

        verifyNoInteractions(authorizationService);
    }
}
