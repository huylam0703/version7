package vn.farm.starter.security;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    @Value("${farm.security.enabled:true}")
    private boolean securityEnabled;

    @Value("${farm.security.dev-user-id:00000000-0000-0000-0000-000000000001}")
    private UUID devUserId;

    /** UserId lấy từ claim "sub" của JWT (ghi vào CreatedBy...). */
    public UUID id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a instanceof JwtAuthenticationToken t) return UUID.fromString(t.getToken().getSubject());
        if (!securityEnabled) return devUserId;          // chỉ khi tắt bảo mật để dev riêng lẻ
        throw new AccessDeniedException("Chưa đăng nhập.");
    }
}