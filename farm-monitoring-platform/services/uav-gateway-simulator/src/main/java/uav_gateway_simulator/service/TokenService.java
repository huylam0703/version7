package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.AuthClient;
import uav_gateway_simulator.dto.TokenResponse;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final AuthClient authClient;

    private String accessToken;
    private Instant expiresAt;

    private static final long EXPIRY_BUFFER_SECONDS = 60;

    public synchronized String getAccessToken() {

        if (accessToken != null
                && expiresAt != null
                && Instant.now().isBefore(expiresAt)) {
            return accessToken;
        }

        TokenResponse response = authClient.requestToken();
        System.out.println(
                "[JWT TEST] Token received: " +
                        (response != null && response.accessToken() != null
                                && !response.accessToken().isBlank())
        );

        if (response != null) {
            System.out.println("[JWT TEST] Token type: " + response.tokenType());
            System.out.println("[JWT TEST] Expires in: " + response.expiresIn());
            System.out.println("[JWT TEST] Scopes: " + response.scope());
        }

        if (response == null
                || response.accessToken() == null
                || response.accessToken().isBlank()
                || response.expiresIn() <= 0
                || !"Bearer".equalsIgnoreCase(response.tokenType())) {

            throw new IllegalStateException(
                    "Invalid token response from Auth Service"
            );
        }

        this.accessToken = response.accessToken();

        long validSeconds = Math.max(
                0,
                response.expiresIn() - EXPIRY_BUFFER_SECONDS
        );

        this.expiresAt = Instant.now().plusSeconds(validSeconds);

        return accessToken;
    }

    public synchronized void invalidateToken() {
        this.accessToken = null;
        this.expiresAt = null;
    }
}