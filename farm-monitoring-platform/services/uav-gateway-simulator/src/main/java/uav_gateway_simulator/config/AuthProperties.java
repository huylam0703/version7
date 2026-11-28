package uav_gateway_simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration.auth")
public record AuthProperties(
        String tokenUrl,
        String clientId,
        String clientSecret,
        String scope
) {
}