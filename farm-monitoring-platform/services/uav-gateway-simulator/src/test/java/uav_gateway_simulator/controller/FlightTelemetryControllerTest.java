
package uav_gateway_simulator.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;
import uav_gateway_simulator.config.FlightTelemetryProperties;
import uav_gateway_simulator.telemetry.FlightStatus;
import uav_gateway_simulator.telemetry.FlightTelemetryService;
import uav_gateway_simulator.telemetry.FlightTelemetryStreamService;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;
import uav_gateway_simulator.telemetry.FlightTelemetry;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.reactive.result.method.annotation.AuthenticationPrincipalArgumentResolver;

import reactor.core.publisher.Mono;
import uav_gateway_simulator.telemetry.TelemetryAuthorizationService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

class FlightTelemetryControllerTest {

    private static final String BASE_URL =
            "/api/v1/uav/missions/";
    private String streamUrl(UUID id) {
        return telemetryUrl(id) + "/stream";
    }

    private FlightTelemetryService telemetryService;
    private WebTestClient webTestClient;

    private UUID missionId;
    private UUID executionId;
    private TelemetryAuthorizationService authorizationService;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        executionId = UUID.randomUUID();

        configure(true);
    }


    private void configure(boolean enabled) {
        FlightTelemetryProperties properties =
                new FlightTelemetryProperties(
                        enabled,
                        1000L,
                        0.02,
                        100.0,
                        1000L,
                        0L
                );

        this.telemetryService =
                new FlightTelemetryService(properties);

        FlightTelemetryStreamService streamService =
                new FlightTelemetryStreamService(
                        telemetryService,
                        properties
                );

        this.authorizationService =
                mock(TelemetryAuthorizationService.class);

        this.jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(authorizationService.authorize(
                any(UUID.class),
                any(Jwt.class)
        )).thenReturn(Mono.empty());

        FlightTelemetryController controller =
                new FlightTelemetryController(
                        telemetryService,
                        properties,
                        streamService,
                        authorizationService
                );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )
                );

        this.webTestClient = WebTestClient
                .bindToController(controller)
                .argumentResolvers(configurer ->
                        configurer.addCustomResolver(
                                new AuthenticationPrincipalArgumentResolver(
                                        ReactiveAdapterRegistry.getSharedInstance()
                                )
                        )
                )
                .webFilter((exchange, chain) ->
                        chain.filter(exchange)
                                .contextWrite(
                                        org.springframework.security.core.context
                                                .ReactiveSecurityContextHolder
                                                .withAuthentication(authentication)
                                )
                )
                .build();
    }


    private String telemetryUrl(UUID id) {
        return BASE_URL + id + "/telemetry";
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
    void shouldReturnLatestTelemetry() {
        initializeTelemetry();

        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.missionId")
                .isEqualTo(missionId.toString())
                .jsonPath("$.executionId")
                .isEqualTo(executionId.toString())
                .jsonPath("$.latitude").isEqualTo(10.75)
                .jsonPath("$.longitude").isEqualTo(106.68)
                .jsonPath("$.altitudeM").isEqualTo(20.0)
                .jsonPath("$.batteryPercent").isEqualTo(100.0)
                .jsonPath("$.flightStatus").isEqualTo("READY")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void shouldReturnNotFoundWhenTelemetryMissing() {
        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRejectInvalidMissionId() {
        webTestClient.get()
                .uri(BASE_URL + "invalid-uuid/telemetry")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturnUnavailableWhenTelemetryDisabled() {
        configure(false);
        initializeTelemetry();

        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void shouldDisableResponseCaching() {
        initializeTelemetry();

        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .valueEquals("Cache-Control", "no-store");
    }

    @Test
    void shouldReturnCompletedTelemetry() {
        initializeTelemetry();

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.flightStatus")
                .isEqualTo("COMPLETED")
                .jsonPath("$.speedMps")
                .isEqualTo(0.0);
    }

    @Test
    void shouldNotReturnTelemetryFromAnotherMission() {
        initializeTelemetry();

        webTestClient.get()
                .uri(telemetryUrl(UUID.randomUUID()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturnUpdatedBatteryPercent() {
        initializeTelemetry();

        telemetryService.consumeBattery(
                missionId,
                executionId,
                15.0
        );

        webTestClient.get()
                .uri(telemetryUrl(missionId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.batteryPercent")
                .isEqualTo(85.0);
    }


    @Test
    void shouldReturnSseContentType() {
        initializeTelemetry();

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        webTestClient.get()
                .uri(streamUrl(missionId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM
                )
                .expectBody()
                .consumeWith(result ->
                        assertNotNull(result.getResponseBody())
                );
    }

    @Test
    void shouldReturnNotFoundForMissingStream() {
        webTestClient.get()
                .uri(streamUrl(missionId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldRejectInvalidStreamMissionId() {
        webTestClient.get()
                .uri(BASE_URL + "invalid-uuid/telemetry/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRejectStreamWhenTelemetryDisabled() {
        configure(false);
        initializeTelemetry();

        webTestClient.get()
                .uri(streamUrl(missionId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void shouldReturnSseCacheAndBufferingHeaders() {
        initializeTelemetry();

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        webTestClient.get()
                .uri(streamUrl(missionId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader()
                .valueEquals("Cache-Control", "no-store")
                .expectHeader()
                .valueEquals("X-Accel-Buffering", "no")
                .expectBody()
                .consumeWith(result ->
                        assertNotNull(result.getResponseBody())
                );
    }

    @Test
    void shouldStreamInitialTelemetryEvent() {
        initializeTelemetry();

        var response = webTestClient.get()
                .uri(streamUrl(missionId))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(
                        new ParameterizedTypeReference<
                                ServerSentEvent<FlightTelemetry>>() {}
                );

        StepVerifier.create(response.getResponseBody())
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
    void shouldStreamTerminalEventAndComplete() {
        initializeTelemetry();

        telemetryService.updateStatus(
                missionId,
                executionId,
                FlightStatus.COMPLETED
        );

        var response = webTestClient.get()
                .uri(streamUrl(missionId))
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
                    assertEquals(
                            0.0,
                            event.data().speedMps()
                    );
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void shouldIsolateSseStreamsBetweenMissions() {
        initializeTelemetry();

        webTestClient.get()
                .uri(streamUrl(UUID.randomUUID()))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();
    }

}
