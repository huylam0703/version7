package uav_gateway_simulator.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensorDataClientIntegrationTest {

    @Test
    void postsACompleteBatchWithUtcTimestampsAndReadsAcknowledgments() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var request = new AtomicReference<String>();
        var method = new AtomicReference<String>();
        server.createContext("/api/v1/sync-batches", exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = """
                        {"total":2,"validCount":1,"invalidCount":0,"duplicateCount":1,
                         "items":[{"index":0,"status":"VALID","id":17},{"index":1,"status":"DUPLICATE"}]}
                        """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        try {
            var client = new SensorDataClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
            var measured = LocalDateTime.parse("2026-11-28T03:00:00.123");
            var collected = measured.plusSeconds(2);
            var response = client.sendBatch(List.of(
                    new SensorReadingRequest("node-1", "WATER_LEVEL", 0d, measured, collected,
                            10.03, 105.78, "ADC_COUNT", "ADC_RAW", 12),
                    new SensorReadingRequest("node-1", "SOIL_MOISTURE", 2425d, measured, collected,
                            10.03, 105.78, "ADC_COUNT", "ADC_RAW", 12)
            ));

            assertEquals("POST", method.get());
            var readings = new JsonMapper().readTree(request.get()).get("readings");
            assertEquals(2, readings.size());
            var reading = readings.get(0);
            assertEquals("node-1", reading.get("nodeId").asString());
            assertEquals("WATER_LEVEL", reading.get("sensorType").asString());
            assertEquals(0.0, reading.get("sensorValue").asDouble());
            assertEquals("2026-11-28T03:00:00.123Z", reading.get("measuredAt").asString());
            assertEquals("2026-11-28T03:00:02.123Z", reading.get("collectedAt").asString());
            assertEquals(10.03, reading.get("latitude").asDouble());
            assertEquals(105.78, reading.get("longitude").asDouble());
            assertEquals("ADC_COUNT", reading.get("unit").asString());
            assertEquals("ADC_RAW", reading.get("valueKind").asString());
            assertEquals(12, reading.get("adcResolutionBits").asInt());
            assertEquals(17L, response.items().getFirst().id());
            assertEquals("DUPLICATE", response.items().getLast().status());
        } finally {
            server.stop(0);
        }
    }
}
