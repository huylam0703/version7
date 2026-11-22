package vn.farm.farmdevice.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.entity.ConnectionType;

class ConnectionRulesTest {

    @Test
    void acceptsValidEndpoints() {
        assertThatCode(() -> ConnectionRules.check(ConnectionType.HTTP, "http://192.168.1.50:8080/api")).doesNotThrowAnyException();
        assertThatCode(() -> ConnectionRules.check(ConnectionType.HTTPS, "https://node1.farm.local")).doesNotThrowAnyException();
        assertThatCode(() -> ConnectionRules.check(ConnectionType.MQTT, "mqtt://broker.local:1883")).doesNotThrowAnyException();
        assertThatCode(() -> ConnectionRules.check(ConnectionType.TCP, "tcp://10.0.0.5:5000")).doesNotThrowAnyException();
    }

    @Test
    void bothNullMeansNoEndpoint() {
        assertThatCode(() -> ConnectionRules.check(null, null)).doesNotThrowAnyException();
    }

    @Test
    void needsBothFieldsTogether() {
        assertThatThrownBy(() -> ConnectionRules.check(null, "http://1.2.3.4")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ConnectionRules.check(ConnectionType.HTTP, null)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.168.1.5", "ftp://1.2.3.4", "https://1.2.3.4", "http://", "http://:8080",
            "http://user:pw@1.2.3.4", "http://1.2.3.4:70000", "http://1.2.3.4 /x", "http://1.2.3.4#a", "http://a b"})
    void rejectsBadHttpEndpoints(String url) {
        assertThatThrownBy(() -> ConnectionRules.check(ConnectionType.HTTP, url))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_ENDPOINT");
    }

    @Test
    void rejectsTcpWithoutPortAndTooLong() {
        assertThatThrownBy(() -> ConnectionRules.check(ConnectionType.TCP, "tcp://10.0.0.5"))
                .isInstanceOf(BusinessException.class);
        String longUrl = "http://" + "a".repeat(260) + ".com";
        assertThatThrownBy(() -> ConnectionRules.check(ConnectionType.HTTP, longUrl))
                .isInstanceOf(BusinessException.class);
    }
}
