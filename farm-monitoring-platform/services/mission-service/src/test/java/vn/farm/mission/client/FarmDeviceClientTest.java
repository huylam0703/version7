package vn.farm.mission.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import vn.farm.mission.dto.integration.MissionCheckRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FarmDeviceClientTest {
    @Test
    void sendsBearerToken() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var authorization = new AtomicReference<String>();
        server.createContext("/internal/v1/references/mission-check", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"valid\":true,\"problems\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var provider = mock(ServiceTokenProvider.class);
            when(provider.getAccessToken()).thenReturn("test-token");
            var client = new FarmDeviceClient("http://127.0.0.1:" + server.getAddress().getPort(), provider);
            var request = new MissionCheckRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()));
            assertTrue(client.checkMissionReferences(request).isValid());
            assertEquals("Bearer test-token", authorization.get());
        } finally { server.stop(0); }
    }
}
