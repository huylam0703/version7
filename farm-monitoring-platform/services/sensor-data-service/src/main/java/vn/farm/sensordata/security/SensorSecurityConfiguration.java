package vn.farm.sensordata.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
class SensorSecurityConfiguration {

    private static final String LEGACY_READINGS = "/api/sensor-readings";
    private static final String READINGS = "/api/v1/readings";
    private static final String[] READ_PATHS = {LEGACY_READINGS, READINGS, READINGS + "/**"};
    private static final String[] WRITE_PATHS = {
            LEGACY_READINGS, READINGS, READINGS + "/batch", "/api/v1/sync-batches"
    };
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority SERVICE = new SimpleGrantedAuthority("ROLE_SERVICE");

    @Bean
    SecurityFilterChain sensorSecurityChain(
            HttpSecurity http, JwtDecoder jwtDecoder,
            @Value("${SENSOR_WRITER_CLIENT_IDS:uav-simulator-01}") String writerClientIds
    ) throws Exception {
        Set<String> allowedWriters = Arrays.stream(writerClientIds.split(","))
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml").permitAll()
                        .requestMatchers(HttpMethod.GET, READ_PATHS).hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.HEAD, READ_PATHS).hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.POST, WRITE_PATHS)
                        .access((authentication, request) ->
                                new AuthorizationDecision(canWrite(authentication.get(), allowedWriters)))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(jwtDecoder)
                        .jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    JwtDecoder sensorJwtDecoder(
            @Value("${AUTH_JWKS_URI:http://localhost:8081/.well-known/jwks.json}") String jwksUri,
            @Value("${AUTH_ISSUER:farm-auth}") String issuer,
            @Value("${AUTH_AUDIENCE:farm-api}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)
        ));
        return decoder;
    }

    private static boolean canWrite(Authentication authentication, Set<String> allowedWriters) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return false;
        }
        if (jwt.getAuthorities().contains(ADMIN)) {
            return true;
        }
        String subject = jwt.getToken().getSubject();
        return subject != null && allowedWriters.contains(subject)
                && (subject.equals(jwt.getToken().getClaimAsString("client_id"))
                || jwt.getAuthorities().contains(SERVICE));
    }
}
