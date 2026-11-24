package vn.farm.sensordata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.farm.sensordata.domain.ReadingContext;
import vn.farm.sensordata.domain.ReadingValueKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public record ReadingSubmission(
        @NotBlank @Size(max = 100) String nodeId,
        @NotBlank @Size(max = 50) String sensorType,
        @NotNull Double sensorValue,
        @NotNull Instant measuredAt,
        @NotNull Instant collectedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 20) @Schema(example = "°C") String unit,
        ReadingValueKind valueKind,
        @Digits(integer = 2, fraction = 0)
        @Schema(type = "integer", description = "Required for ADC_RAW") BigDecimal adcResolutionBits,
        UUID farmId,
        UUID zoneId,
        UUID missionId,
        UUID executionId
) {
    public SensorReadingRequest reading() {
        return new SensorReadingRequest(nodeId, sensorType, sensorValue,
                LocalDateTime.ofInstant(measuredAt, ZoneOffset.UTC), LocalDateTime.ofInstant(collectedAt, ZoneOffset.UTC),
                latitude, longitude, unit, valueKind, adcResolutionBits);
    }

    public ReadingContext context() {
        return new ReadingContext(farmId, zoneId, missionId, executionId);
    }
}
