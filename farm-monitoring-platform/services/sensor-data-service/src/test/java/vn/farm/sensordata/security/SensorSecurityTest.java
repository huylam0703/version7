package vn.farm.sensordata.security;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import vn.farm.sensordata.api.ReadingV1Controller;
import vn.farm.sensordata.api.SensorReadingController;
import vn.farm.sensordata.api.dto.ReadingResults.*;
import vn.farm.sensordata.api.dto.ReadingSubmission;
import vn.farm.sensordata.api.dto.SensorReadingRequest;
import vn.farm.sensordata.application.ReadingQueryService;
import vn.farm.sensordata.application.SensorReadingService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ReadingV1Controller.class, SensorReadingController.class})
@Import(SensorSecurityConfiguration.class)
class SensorSecurityTest {

    private static final String LEGACY = "/api/sensor-readings";
    private static final String READINGS = "/api/v1/readings";
    private static final String BATCH = READINGS + "/batch";
    private static final String SYNC = "/api/v1/sync-batches";
    private static final String ISSUER = "farm-auth";
    private static final String AUDIENCE = "farm-api";
    private static final String WRITER = "test-simulator";
    private static final RSAKey TRUSTED_KEY = newKey();
    private static final RSAKey UNTRUSTED_KEY = newKey();
    private static final HttpServer JWKS = startJwksServer();
    private static final String MEASUREMENT = """
            {"nodeId":"node-1","sensorType":"LIGHT","sensorValue":120,"unit":"lux","valueKind":"PHYSICAL",
             "measuredAt":"2026-11-28T02:10:00Z","collectedAt":"2026-11-28T02:10:01Z",
             "latitude":10.03,"longitude":105.78}
            """;
    private static final String MEASUREMENTS = "{\"readings\":[" + MEASUREMENT + "]}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SensorReadingService ingest;

    @MockitoBean
    private ReadingQueryService query;

    @DynamicPropertySource
    static void trustedIssuer(DynamicPropertyRegistry registry) {
        registry.add("AUTH_JWKS_URI",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/.well-known/jwks.json");
        registry.add("AUTH_ISSUER", () -> ISSUER);
        registry.add("AUTH_AUDIENCE", () -> AUDIENCE);
        registry.add("SENSOR_WRITER_CLIENT_IDS", () -> " test-simulator, test-service ");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS.stop(0);
    }

    @Test
    void requiresJwtOnLegacyAndV1ReadsAndWrites() throws Exception {
        for (String path : List.of(LEGACY, READINGS, READINGS + "/latest")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : List.of(LEGACY, READINGS, BATCH, SYNC)) {
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(ingest, query);
    }

    @Test
    void adminCanReadAndWriteBothApiVersions() throws Exception {
        String admin = token("admin-user", "ADMIN", null);
        when(ingest.getLatestReadings(anyInt())).thenReturn(List.of());
        when(query.list(any(), any(), eq(false))).thenReturn(new Page(List.of(), 1, 20, 0));
        when(ingest.saveReading(any(SensorReadingRequest.class)))
                .thenReturn(new Written(WriteStatus.VALID, "Saved", 1L));
        when(ingest.saveReading(any(ReadingSubmission.class)))
                .thenReturn(new Written(WriteStatus.VALID, "Saved", 2L));
        stubAcceptedBatch();

        mvc.perform(get(LEGACY).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        mvc.perform(get(READINGS).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        mvc.perform(post(LEGACY).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(MEASUREMENT.replace("Z\"", "\"")))
                .andExpect(status().isCreated());
        mvc.perform(post(READINGS).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(MEASUREMENT)).andExpect(status().isCreated());
        for (String path : List.of(BATCH, SYNC)) {
            mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, admin)
                    .contentType(MediaType.APPLICATION_JSON).content(MEASUREMENTS)).andExpect(status().isOk());
        }
    }

    @Test
    void operatorCanReadButCannotWrite() throws Exception {
        String operator = token("operator-user", "OPERATOR", null);
        when(query.list(any(), any(), eq(true))).thenReturn(new Page(List.of(), 1, 20, 0));
        mvc.perform(get(READINGS + "/latest").header(HttpHeaders.AUTHORIZATION, operator))
                .andExpect(status().isOk());

        for (String path : List.of(LEGACY, READINGS, BATCH, SYNC)) {
            mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, operator))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(ingest);
    }

    @Test
    void allowsOnlyConfiguredMachineClientsToWrite() throws Exception {
        stubAcceptedBatch();
        String simulator = token(WRITER, null, WRITER);
        String service = token("test-service", "SERVICE", null);
        for (String authorization : List.of(simulator, service)) {
            mvc.perform(post(SYNC).header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_JSON).content(MEASUREMENTS)).andExpect(status().isOk());
            mvc.perform(get(READINGS).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
        }

        for (String authorization : List.of(token("other-client", null, "other-client"),
                token("other-client", null, WRITER), token(WRITER, "OPERATOR", null),
                token("other-service", "SERVICE", null))) {
            mvc.perform(post(SYNC).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsInvalidSignatureIssuerAudienceAndExpiration() throws Exception {
        Date validUntil = Date.from(Instant.now().plusSeconds(300));
        for (String authorization : List.of(
                token("admin-user", "ADMIN", null, UNTRUSTED_KEY, ISSUER, AUDIENCE, validUntil),
                token("admin-user", "ADMIN", null, TRUSTED_KEY, "other-issuer", AUDIENCE, validUntil),
                token("admin-user", "ADMIN", null, TRUSTED_KEY, ISSUER, "other-api", validUntil),
                token("admin-user", "ADMIN", null, TRUSTED_KEY, ISSUER, AUDIENCE, null),
                token("admin-user", "ADMIN", null, TRUSTED_KEY, ISSUER, AUDIENCE,
                        Date.from(Instant.now().minusSeconds(300))))) {
            mvc.perform(get(READINGS).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(ingest, query);
    }

    private void stubAcceptedBatch() {
        when(ingest.saveBatch(any())).thenReturn(new Batch(1, 1, 0, 0,
                List.of(new BatchItem(0, WriteStatus.VALID, 3L))));
    }

    private static String token(String subject, String role, String clientId) throws JOSEException {
        return token(subject, role, clientId, TRUSTED_KEY, ISSUER, AUDIENCE,
                Date.from(Instant.now().plusSeconds(300)));
    }

    private static String token(String subject, String role, String clientId,
                                RSAKey key, String issuer, String audience, Date expires) throws JOSEException {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject)
                .expirationTime(expires).claim("role", role).claim("client_id", clientId);
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return "Bearer " + jwt.serialize();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("sensor-test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not create test signing key", e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(TRUSTED_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jwks.length);
                    exchange.getResponseBody().write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start local test JWKS endpoint", e);
        }
    }
}
