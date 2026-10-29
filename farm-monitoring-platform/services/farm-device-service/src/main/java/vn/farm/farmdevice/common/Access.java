package vn.farm.farmdevice.common;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Dùng trong @PreAuthorize("@access.admin()") theo Auth Matrix của workbook API.
 * Khi farm.security.enabled=false (dev chạy riêng) thì luôn cho qua, nên không cần comment @PreAuthorize nữa.
 */
@Component("access")
public class Access {
    @Value("${farm.security.enabled:true}")
    private boolean enabled;

    /** Đọc farm/zone/node/uav/gateway/sensor-type. */
    public boolean read() { return has("ADMIN", "OPERATOR", "SERVICE"); }

    /** Ghi farm/zone. */
    public boolean admin() { return has("ADMIN"); }

    /** Ghi node/UAV/gateway (OPERATOR, ADMIN được phép thêm). Muốn bỏ ADMIN thì sửa ở đây. */
    public boolean deviceWrite() { return has("OPERATOR", "ADMIN"); }

    private boolean has(String... roles) {
        if (!enabled) return true;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return false;
        Set<String> have = a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        for (String r : roles) if (have.contains("ROLE_" + r)) return true;
        return false;
    }
}
