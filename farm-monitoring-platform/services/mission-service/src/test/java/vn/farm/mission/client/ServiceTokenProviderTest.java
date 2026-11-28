package vn.farm.mission.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ServiceTokenProviderTest {
    @Test
    void cachesAndRefreshesToken() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = mock(Clock.class);
        var start = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var provider = new ServiceTokenProvider(builder.build(), "http://auth/oauth2/token",
                "mission-service", "test-secret", clock);
        server.expect(requestTo("http://auth/oauth2/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string("grant_type=client_credentials&client_id=mission-service&client_secret=test-secret"))
                .andRespond(withSuccess("{\"access_token\":\"first\",\"expires_in\":900}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/oauth2/token"))
                .andRespond(withSuccess("{\"access_token\":\"second\",\"expires_in\":900}", MediaType.APPLICATION_JSON));
        assertEquals("first", provider.getAccessToken());
        when(clock.instant()).thenReturn(start.plusSeconds(869));
        assertEquals("first", provider.getAccessToken());
        when(clock.instant()).thenReturn(start.plusSeconds(870));
        assertEquals("second", provider.getAccessToken());
        server.verify();
    }

    @Test
    void missingSecretFailsBeforeRequest() {
        var provider = new ServiceTokenProvider(RestClient.create(), "http://auth/oauth2/token",
                "mission-service", "", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThrows(IllegalStateException.class, provider::getAccessToken);
    }
}
