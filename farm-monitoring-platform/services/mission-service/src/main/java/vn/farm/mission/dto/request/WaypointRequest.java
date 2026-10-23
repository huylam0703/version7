package vn.farm.mission.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaypointRequest {
    @NotNull
    @Min(1)
    @Max(32767)
    Integer seq;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    BigDecimal lat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    BigDecimal lng;

    @DecimalMin("0.0")
    @DecimalMax("9999.99")
    BigDecimal altitudeM;
}
