package vn.farm.mission.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaypointResponse {
    UUID waypointId;
    int seq;
    BigDecimal lat;
    BigDecimal lng;
    BigDecimal altitudeM;
    String status;
}
