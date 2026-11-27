package vn.farm.farmdevice.common;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.entity.ConnectionType;

/** Kiểm tra endpoint kết nối của node: scheme phải khớp connectionType, có host, port hợp lệ, không nhúng tài khoản. */
public final class ConnectionRules {
    private ConnectionRules() {}

    public static final int MAX_LENGTH = 255;

    private static final Map<ConnectionType, Set<String>> SCHEMES = Map.of(
            ConnectionType.HTTP, Set.of("http"),
            ConnectionType.HTTPS, Set.of("https"),
            ConnectionType.MQTT, Set.of("mqtt", "mqtts"),
            ConnectionType.TCP, Set.of("tcp"));

    /** Cả hai cùng null = node chưa có endpoint (hợp lệ). Có một trong hai thì phải có đủ cả hai. */
    public static void check(ConnectionType type, String endpointUrl) {
        if (type == null && endpointUrl == null) return;
        if (type == null) throw bad("connectionType là bắt buộc khi có endpointUrl.");
        if (endpointUrl == null) throw bad("endpointUrl là bắt buộc khi có connectionType.");

        if (endpointUrl.length() > MAX_LENGTH) throw bad("endpointUrl tối đa " + MAX_LENGTH + " ký tự.");
        if (endpointUrl.chars().anyMatch(Character::isWhitespace)) throw bad("endpointUrl không được chứa khoảng trắng.");

        URI uri;
        try {
            uri = new URI(endpointUrl);
        } catch (Exception e) {
            throw bad("endpointUrl không đúng định dạng URL.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SCHEMES.get(type).contains(scheme))
            throw bad("endpointUrl phải bắt đầu bằng " + String.join(" hoặc ", SCHEMES.get(type).stream().sorted()
                    .map(s -> s + "://").toList()) + " khi connectionType là " + type + ".");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw bad("endpointUrl thiếu host/IP.");
        if (isForbiddenHost(uri.getHost()))
            throw bad("endpointUrl không được trỏ tới localhost, loopback hoặc địa chỉ link-local/metadata.");
        if (uri.getUserInfo() != null) throw bad("endpointUrl không được chứa tài khoản/mật khẩu.");
        if (uri.getFragment() != null) throw bad("endpointUrl không được chứa '#'.");
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) throw bad("Port của endpointUrl phải trong 1..65535.");
        if ((type == ConnectionType.TCP) && port == -1) throw bad("endpointUrl kiểu TCP phải có port.");
    }

    /** Chặn localhost, 127.x, 0.0.0.0, ::1, 169.254.x (metadata cloud). IP LAN 192.168.x/10.x của ESP32 vẫn được phép. */
    static boolean isForbiddenHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        return h.equals("localhost") || h.endsWith(".localhost")
                || h.equals("0.0.0.0") || h.equals("::1") || h.equals("::")
                || h.startsWith("127.") || h.startsWith("169.254.")
                || h.equals("metadata.google.internal");
    }

    private static BusinessException bad(String msg) {
        return BusinessException.invalid("INVALID_ENDPOINT", msg);
    }
}
