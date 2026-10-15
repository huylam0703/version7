package vn.farm.starter.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

public class ServiceTokenProvider {
    private final RestClient http;
    private final String tokenUri, clientId, clientSecret;
    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(RestClient.Builder builder, String tokenUri, String clientId, String clientSecret) {
        this.http = builder.build();
        this.tokenUri = tokenUri; this.clientId = clientId; this.clientSecret = clientSecret;
    }

    public String get() {
        if (Instant.now().isBefore(expiresAt)) return token;
        synchronized (this) {
            if (Instant.now().isBefore(expiresAt)) return token;
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            Map<String, Object> res = http.post().uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(new ParameterizedTypeReference<>() {});
            token = String.valueOf(res.get("access_token"));
            long ttl = ((Number) res.getOrDefault("expires_in", 300)).longValue();
            expiresAt = Instant.now().plus(Duration.ofSeconds(Math.max(ttl - 30, 5)));
            return token;
        }
    }
}