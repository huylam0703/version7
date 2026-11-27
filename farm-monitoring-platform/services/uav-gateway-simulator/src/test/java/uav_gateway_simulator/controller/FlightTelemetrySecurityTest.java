
package uav_gateway_simulator.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.publisher.Mono;
import uav_gateway_simulator.config.SecurityConfig;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.telemetry.*;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.*;

class FlightTelemetrySecurityTest {

    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID EXECUTION_ID = UUID.randomUUID();

    private static final String REST_URL =
            "/api/v1/uav/missions/" + MISSION_ID + "/telemetry";

    private static final String SSE_URL =
            REST_URL + "/stream";

    private AnnotationConfigApplicationContext context;
    private WebTestClient webTestClient;

    @Configuration
    @EnableWebFlux
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return mock(ReactiveJwtDecoder.class);
        }

        @Bean
        FlightTelemetryProperties telemetryProperties() {
            return new FlightTelemetryProperties(
                    true,
                    1000L,
                    0.02,
                    100.0,
                    1000L,
                    0L
            );
        }

        @Bean
        FlightTelemetryService telemetryService(
                FlightTelemetryProperties properties
        ) {
            FlightTelemetryService service =
                    new FlightTelemetryService(properties);

            service.initialize(
                    MISSION_ID,
                    EXECUTION_ID,
                    10.75,
                    106.68,
                    20.0
            );

            service.updateStatus(
                    MISSION_ID,
                    EXECUTION_ID,
                    FlightStatus.COMPLETED
            );

            return service;
        }

        @Bean
        FlightTelemetryStreamService streamService(
                FlightTelemetryService service,
                FlightTelemetryProperties properties
        ) {
            return new FlightTelemetryStreamService(
                    service,
                    properties
            );
        }

        @Bean
        TelemetryAuthorizationService authorizationService() {
            TelemetryAuthorizationService service =
                    mock(TelemetryAuthorizationService.class);

            when(service.authorize(any(), any()))
                    .thenReturn(Mono.empty());

            return service;
        }

        @Bean
        FlightTelemetryController controller(
                FlightTelemetryService service,
                FlightTelemetryProperties properties,
                FlightTelemetryStreamService streamService,
                TelemetryAuthorizationService authorizationService
        ) {
            return new FlightTelemetryController(
                    service,
                    properties,
                    streamService,
                    authorizationService
            );
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(
                TestConfig.class
        );

        webTestClient = WebTestClient
                .bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    private WebTestClient authenticatedClient(String role) {
        return webTestClient.mutateWith(
                mockJwt()
                        .jwt(jwt -> jwt
                                .subject(UUID.randomUUID().toString())
                                .claim("role", role)
                                .expiresAt(
                                        java.time.Instant.now()
                                                .plusSeconds(3600)
                                )
                        )
                        .authorities(
                                new org.springframework.security.core
                                        .authority.SimpleGrantedAuthority(
                                        "ROLE_" + role
                                )
                        )
        );
    }

    @Test
    void shouldRejectUnauthenticatedRestRequest() {
        webTestClient.get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectUnauthenticatedSseRequest() {
        webTestClient.get()
                .uri(SSE_URL)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAdminRestRequest() {
        authenticatedClient("ADMIN")
                .get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.missionId")
                .isEqualTo(MISSION_ID.toString());
    }

    @Test
    void shouldAllowOperatorRestRequest() {
        authenticatedClient("OPERATOR")
                .get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectServiceRoleRestRequest() {
        authenticatedClient("SERVICE")
                .get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectUnknownRoleRestRequest() {
        authenticatedClient("UNKNOWN")
                .get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldAllowAdminSseRequest() {
        authenticatedClient("ADMIN")
                .get()
                .uri(SSE_URL)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM
                );
    }

    @Test
    void shouldAllowOperatorSseRequest() {
        authenticatedClient("OPERATOR")
                .get()
                .uri(SSE_URL)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectServiceRoleSseRequest() {
        authenticatedClient("SERVICE")
                .get()
                .uri(SSE_URL)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectClientCredentialsScope() {
        webTestClient.mutateWith(
                        mockJwt().authorities(
                                new org.springframework.security.core
                                        .authority.SimpleGrantedAuthority(
                                        "SCOPE_mission:read"
                                )
                        )
                )
                .get()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldDenyUnlistedEndpoint() {
        authenticatedClient("ADMIN")
                .get()
                .uri("/api/v1/uav/internal")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldRejectUnauthorizedHttpMethod() {
        authenticatedClient("ADMIN")
                .post()
                .uri(REST_URL)
                .exchange()
                .expectStatus().isForbidden();
    }
}
