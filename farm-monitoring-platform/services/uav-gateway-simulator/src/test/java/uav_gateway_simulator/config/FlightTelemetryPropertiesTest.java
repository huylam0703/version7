package uav_gateway_simulator.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class FlightTelemetryPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FlightTelemetryProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FlightTelemetryProperties.class))
                    .isEqualTo(new FlightTelemetryProperties(true, 1000L, 0.02, 100.0, 1000L, 0L));
        });
    }

    @Test
    void shouldBindCustomValues() {
        runner.withPropertyValues(
                "simulator.telemetry.enabled=false",
                "simulator.telemetry.update-interval-ms=50",
                "simulator.telemetry.battery-drain-per-second=2.5",
                "simulator.telemetry.initial-battery-percent=80",
                "simulator.telemetry.sse-publish-interval-ms=250",
                "simulator.telemetry.sse-timeout-ms=5000"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FlightTelemetryProperties.class))
                    .isEqualTo(new FlightTelemetryProperties(false, 50L, 2.5, 80.0, 250L, 5000L));
        });
    }

    private void reject(String property) {
        runner.withPropertyValues("simulator.telemetry." + property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void shouldRejectZeroUpdateInterval() { reject("update-interval-ms=0"); }
    @Test void shouldRejectNegativeUpdateInterval() { reject("update-interval-ms=-1"); }
    @Test void shouldRejectNegativeDrain() { reject("battery-drain-per-second=-0.01"); }
    @Test void shouldRejectExcessiveDrain() { reject("battery-drain-per-second=101"); }
    @Test void shouldRejectNegativeBattery() { reject("initial-battery-percent=-1"); }
    @Test void shouldRejectExcessiveBattery() { reject("initial-battery-percent=101"); }
    @Test void shouldRejectZeroSseInterval() { reject("sse-publish-interval-ms=0"); }
    @Test void shouldRejectNegativeSseTimeout() { reject("sse-timeout-ms=-1"); }
}
