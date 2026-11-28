package vn.farm.mission.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class ServiceTokenProvider {
    private final RestClient client;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant refreshAt = Instant.EPOCH;

    @Autowired
    public ServiceTokenProvider(
            @Value("${integration.auth.token-url}") String tokenUrl,
            @Value("${integration.auth.client-id}") String clientId,
            @Value("${integration.auth.client-secret:}") String clientSecret) {
        this(buildClient(), tokenUrl, clientId, clientSecret, Clock.systemUTC());
    }

    ServiceTokenProvider(RestClient client, String tokenUrl, String clientId,
                         String clientSecret, Clock clock) {
        this.client = client;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    private static RestClient buildClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    public synchronized String getAccessToken() {
        if (token != null && clock.instant().isBefore(refreshAt)) {
            return token;
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("MISSION_CLIENT_SECRET is required for remote reference validation");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Instant requestedAt = clock.instant();
        TokenResponse response = client.post().uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(TokenResponse.class);
        if (response == null || response.accessToken() == null
                || response.accessToken().isBlank() || response.expiresIn() <= 0) {
            throw new IllegalStateException("User Auth returned an invalid service token response");
        }
        Instant expiresAt = requestedAt.plusSeconds(response.expiresIn());
        if (!clock.instant().isBefore(expiresAt)) {
            throw new IllegalStateException("User Auth returned an expired service token");
        }
        token = response.accessToken();
        refreshAt = expiresAt.minusSeconds(Math.min(30, response.expiresIn() / 2));
        return token;
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {}
}
