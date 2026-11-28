package vn.farm.alert;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import vn.farm.alert.dto.event.EventEnvelope;
import vn.farm.alert.dto.event.ReadingsSyncedPayload;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.listener.SensorReadingListener;
import vn.farm.alert.outbox.AlertOutboxRepository;
import vn.farm.alert.publisher.AlertEventPublisher;
import vn.farm.alert.repository.AlertRepository;
import vn.farm.alert.service.AlertService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:alert_task9;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false",
        "alert.outbox.enabled=false"
})
@AutoConfigureMockMvc
class AlertApiIntegrationTests {

    private static final String ALERTS = "/api/v1/alert/alerts";
    private static final RSAKey SIGNING_KEY = newSigningKey();
    private static HttpServer jwksServer;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlertRepository repository;

    @Autowired
    private SensorReadingListener readings;

    @Autowired
    private AlertOutboxRepository outbox;

    @Autowired
    private AlertEventPublisher publisher;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private AlertService alertService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private AmqpAdmin amqpAdmin;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry properties) {
        // Serve a public JWK locally and let the production starter decode real bearer tokens.
        try {
            jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        byte[] keys = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer.createContext("/.well-known/jwks.json", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, keys.length);
                exchange.getResponseBody().write(keys);
            }
        });
        jwksServer.start();
        String uri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/.well-known/jwks.json";
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> uri);
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @BeforeEach
    void clearAlerts() {
        outbox.deleteAll();
        repository.deleteAll();
    }

    @Test
    void retriesPendingAlertWithTheSameEventId() throws Exception {
        createAlert(UUID.randomUUID(), "ADMIN");
        var pending = outbox.findAll().getFirst();
        var eventId = pending.getEventId();

        doAnswer(invocation -> {
            throw new AmqpConnectException(new ConnectException("Broker unavailable"));
        }).when(rabbitTemplate).send(eq("farm.events"), eq("alert.created"),
                any(Message.class), any(CorrelationData.class));

        assertFalse(publisher.publish(eventId));
        var retry = outbox.findById(eventId).orElseThrow();
        assertEquals(1L, retry.getAttempts());
        assertNull(retry.getPublishedAt());
        assertEquals(1, repository.count());

        jdbc.update("update dbo_AL_OutboxEvents set NextRetryAt = CURRENT_TIMESTAMP");
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
            assertEquals(pending.getPayload(), new String(message.getBody(), StandardCharsets.UTF_8));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("farm.events"), eq("alert.created"),
                any(Message.class), any(CorrelationData.class));

        assertTrue(publisher.publish(eventId));
        assertNotNull(outbox.findById(eventId).orElseThrow().getPublishedAt());
        assertEquals(1, outbox.count());
    }

    @Test
    void rollsBackAlertAndOutboxTogether() {
        var request = new CreateAlertRequest();
        request.setNodeId(UUID.randomUUID());
        request.setAlertType("OUT_OF_RANGE");
        request.setSeverity(AlertSeverity.HIGH);
        request.setMessage("Test rollback");

        transactions.executeWithoutResult(status -> {
            alertService.createAlert(request);
            assertEquals(1, outbox.count());
            status.setRollbackOnly();
        });

        assertEquals(0, repository.count());
        assertEquals(0, outbox.count());
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get(ALERTS).contextPath("/api/v1/alert"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String expired = token("ADMIN", Instant.now().minusSeconds(300), SIGNING_KEY);
        mvc.perform(get(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() throws Exception {
        String token = token("ADMIN", Instant.now().plusSeconds(300), newSigningKey());
        mvc.perform(get(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceRoleCannotReadAlerts() throws Exception {
        mvc.perform(get(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, bearer("SERVICE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    void userCanReadButCannotCreateAcknowledgeOrResolve() throws Exception {
        createAlert(UUID.randomUUID(), "ADMIN");
        UUID alertId = repository.findAll().getFirst().getId();
        String user = bearer("USER");
        mvc.perform(get(ALERTS + "/{id}", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("NEW"));

        mvc.perform(post(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, user)
                        .contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ALERTS + "/{id}/acknowledge", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ALERTS + "/{id}/resolve", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        assertEquals(1, repository.count());
        assertEquals(AlertStatus.NEW, repository.findById(alertId).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OPERATOR"})
    void createsQueriesAndUpdatesAlertsInDatabase(String role) throws Exception {
        UUID nodeId = UUID.randomUUID();
        createAlert(nodeId, role);
        UUID alertId = repository.findAll().getFirst().getId();
        String authorization = bearer(role);

        mvc.perform(get(ALERTS + "/node/{nodeId}/status/NEW", nodeId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(1)))
                .andExpect(jsonPath("$.result[0].id").value(alertId.toString()));

        mvc.perform(patch(ALERTS + "/{id}/resolve", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1009));

        mvc.perform(patch(ALERTS + "/{id}/acknowledge", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACKNOWLEDGED"));
        assertEquals(AlertStatus.ACKNOWLEDGED, repository.findById(alertId).orElseThrow().getStatus());

        mvc.perform(patch(ALERTS + "/{id}/resolve", alertId).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RESOLVED"));
        assertEquals(AlertStatus.RESOLVED, repository.findById(alertId).orElseThrow().getStatus());

        mvc.perform(get(ALERTS + "/status/NEW").contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result", hasSize(0)));
    }

    @Test
    void invalidBodyReturns400WithoutSavingAlert() throws Exception {
        mvc.perform(post(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        assertEquals(0, repository.count());
    }

    @Test
    void unknownAlertReturns404() throws Exception {
        mvc.perform(get(ALERTS + "/{id}", UUID.randomUUID()).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, bearer("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void createsOneAlertForAnOutOfRangeReadingEvenWhenTheEventIsRepeated() {
        UUID nodeId = UUID.randomUUID();
        readings.handleReadingsSynced(reading(nodeId, 25.0));
        assertEquals(0, repository.count());

        var outOfRange = reading(nodeId, 45.0);
        readings.handleReadingsSynced(outOfRange);
        readings.handleReadingsSynced(outOfRange);

        assertEquals(1, repository.count());
        var alert = repository.findAll().getFirst();
        assertEquals(nodeId, alert.getNodeId());
        assertEquals(outOfRange.payload().readingId(), alert.getReadingId());
        assertEquals("OUT_OF_RANGE", alert.getAlertType());
        assertEquals(AlertSeverity.HIGH, alert.getSeverity());
    }

    private static EventEnvelope<ReadingsSyncedPayload> reading(UUID nodeId, double temperature) {
        Instant measuredAt = Instant.now();
        var data = new ReadingsSyncedPayload(UUID.randomUUID(), nodeId, null, null, null, null,
                temperature, measuredAt, null, "AIR_TEMP", null);
        return new EventEnvelope<>(UUID.randomUUID(), "readings.synced", 1, measuredAt,
                "sensor-data-service", "", data);
    }

    private void createAlert(UUID nodeId, String role) throws Exception {
        mvc.perform(post(ALERTS).contextPath("/api/v1/alert")
                        .header(HttpHeaders.AUTHORIZATION, bearer(role))
                        .contentType(MediaType.APPLICATION_JSON).content(body(nodeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.nodeId").value(nodeId.toString()))
                .andExpect(jsonPath("$.result.alertType").value("OUT_OF_RANGE"))
                .andExpect(jsonPath("$.result.severity").value("HIGH"))
                .andExpect(jsonPath("$.result.status").value("NEW"));
    }

    private static String body(UUID nodeId) {
        return """
                {"nodeId":"%s","alertType":"OUT_OF_RANGE","severity":"HIGH","message":"Temperature too high"}
                """.formatted(nodeId);
    }

    private static String bearer(String role) throws JOSEException {
        return "Bearer " + token(role, Instant.now().plusSeconds(300), SIGNING_KEY);
    }

    private static String token(String role, Instant expires, RSAKey key) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("farm-auth")
                .audience("farm-api")
                .subject(UUID.randomUUID().toString())
                .claim("role", role)
                .expirationTime(Date.from(expires))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey newSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot generate JWT test key", exception);
        }
    }
}
