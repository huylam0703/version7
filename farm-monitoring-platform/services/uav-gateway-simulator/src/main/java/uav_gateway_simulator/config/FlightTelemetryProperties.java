package uav_gateway_simulator.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "simulator.telemetry")
public record FlightTelemetryProperties(

        @DefaultValue("true")
        boolean enabled,

        @DefaultValue("1000")
        @Min(1)
        long updateIntervalMs,

        @DefaultValue("0.02")
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        double batteryDrainPerSecond,

        @DefaultValue("100.0")
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        double initialBatteryPercent,

        @DefaultValue("1000")
        @Min(1)
        long ssePublishIntervalMs,

        @DefaultValue("0")
        @Min(0)
        long sseTimeoutMs

) {
}