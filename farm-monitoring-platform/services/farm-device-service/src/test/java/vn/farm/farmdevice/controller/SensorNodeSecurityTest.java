package vn.farm.farmdevice.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.farm.farmdevice.common.Access;
import vn.farm.farmdevice.dto.SensorDtos.UpdateNodeRequest;
import vn.farm.farmdevice.service.SensorNodeService;

/**
 * Kiểm tra @PreAuthorize thực sự chặn khi bảo mật BẬT (farm.security.enabled=true),
 * dùng method security thật, không cần user-auth hay JWT.
 */
class SensorNodeSecurityTest {

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean SensorNodeService service() { return mock(SensorNodeService.class); }
        @Bean("access") Access access() { return new Access(); }
        @Bean SensorNodeController controller(SensorNodeService s, Access a) { return new SensorNodeController(s, a); }
    }

    private AnnotationConfigApplicationContext ctx;
    private SensorNodeController controller;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        System.setProperty("farm.security.enabled", "true");
        ctx = new AnnotationConfigApplicationContext(Cfg.class);
        controller = ctx.getBean(SensorNodeController.class);
    }

    @AfterEach
    void tearDown() {
        ctx.close();
        System.clearProperty("farm.security.enabled");
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "n", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static UpdateNodeRequest patch(java.time.Instant lastSeen) {
        return new UpdateNodeRequest(null, null, null, null, null, null, null, null, null, lastSeen);
    }

    @Test
    void connectionNeedsServiceOrAdmin() {
        assertThatThrownBy(() -> controller.connection(id)).as("chưa đăng nhập").isInstanceOf(AccessDeniedException.class);
        loginAs("OPERATOR");
        assertThatThrownBy(() -> controller.connection(id)).isInstanceOf(AccessDeniedException.class);
        loginAs("VIEWER");
        assertThatThrownBy(() -> controller.connection(id)).isInstanceOf(AccessDeniedException.class);
        loginAs("SERVICE");
        assertThatCode(() -> controller.connection(id)).doesNotThrowAnyException();
        loginAs("ADMIN");
        assertThatCode(() -> controller.connection(id)).doesNotThrowAnyException();
    }

    @Test
    void readAllowedForOperatorButNotAnonymous() {
        assertThatThrownBy(() -> controller.get(id)).isInstanceOf(AccessDeniedException.class);
        loginAs("OPERATOR");
        assertThatCode(() -> controller.get(id)).doesNotThrowAnyException();
    }

    @Test
    void writeNeedsOperatorOrAdmin() {
        loginAs("SERVICE");
        assertThatThrownBy(() -> controller.update(id, patch(null))).isInstanceOf(AccessDeniedException.class);
        loginAs("OPERATOR");
        assertThatCode(() -> controller.update(id, patch(null))).doesNotThrowAnyException();
    }

    @Test
    void operatorCannotWriteLastSeenAt() {
        loginAs("OPERATOR");
        assertThatThrownBy(() -> controller.update(id, patch(java.time.Instant.now())))
                .isInstanceOf(AccessDeniedException.class);
        loginAs("ADMIN");
        assertThatCode(() -> controller.update(id, patch(java.time.Instant.now()))).doesNotThrowAnyException();
    }
}
