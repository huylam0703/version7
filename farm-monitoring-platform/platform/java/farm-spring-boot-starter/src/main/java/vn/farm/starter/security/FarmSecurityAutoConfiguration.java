package vn.farm.starter.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import java.util.Set;
import java.util.HashSet;

import org.springframework.security.core.GrantedAuthority;

@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
})
@EnableMethodSecurity
@Import(CurrentUser.class)
public class FarmSecurityAutoConfiguration {
    /** Chế độ thật: bắt buộc JWT; claim "role" -> ROLE_ADMIN / ROLE_OPERATOR. */
    @Bean
    @ConditionalOnProperty(name = "farm.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain jwtChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter roleAuthorities =
                new JwtGrantedAuthoritiesConverter();

        roleAuthorities.setAuthoritiesClaimName("role");
        roleAuthorities.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter scopeAuthorities =
                new JwtGrantedAuthoritiesConverter();

        // Mặc định đọc scope hoặc scp và tạo SCOPE_...

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            Set<GrantedAuthority> combined = new HashSet<>();

            combined.addAll(roleAuthorities.convert(jwt));
            combined.addAll(scopeAuthorities.convert(jwt));

            return combined;
        });

        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/health/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs").permitAll()
                        .requestMatchers("/internal/**").hasRole("SERVICE")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    /** Chế độ dev chạy riêng: mở hết, chưa cần user-auth. */
    @Bean
    @ConditionalOnProperty(name = "farm.security.enabled", havingValue = "false")
    SecurityFilterChain openChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}