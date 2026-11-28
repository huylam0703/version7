package vn.farm.sensordata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import vn.farm.sensordata.domain.ReadingValueKind;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SensorReadingRequest(

        @NotBlank
        @Size(max = 100)
        String nodeId,

        @NotBlank
        @Size(max = 50)
        String sensorType,

        @NotNull
        Double sensorValue,

        @NotNull
        LocalDateTime measuredAt,

        @NotNull
        LocalDateTime collectedAt,

        @NotNull
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        Double latitude,

        @NotNull
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        Double longitude,

        @Size(max = 20)
        @Schema(description = "Unit of sensorValue. Use ADC_COUNT with ADC_RAW.", example = "°C")
        String unit,

        @Schema(description = "Direct physical reading, converted physical reading, or raw ADC count.")
        ReadingValueKind valueKind,

        @Digits(integer = 2, fraction = 0)
        @Schema(type = "integer", description = "ADC output resolution. Required for ADC_RAW only.", example = "12")
        BigDecimal adcResolutionBits
) {
}
