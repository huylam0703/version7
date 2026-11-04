 package vn.farm.sensordata.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record SensorReadingRequest(

        @NotBlank
        String nodeId,

        @NotBlank
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
        Double longitude

) {
}
