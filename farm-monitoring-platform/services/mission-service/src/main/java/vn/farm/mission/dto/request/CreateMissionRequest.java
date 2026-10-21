package vn.farm.mission.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateMissionRequest {
    @NotNull
    UUID farmId;

    @NotNull
    UUID uavId;

    @NotNull
    UUID gatewayId;

    @NotBlank
    @Size(max=150)
    String name;

    @Future
    Instant plannedAt;

    @NotEmpty
    List<@NotNull UUID> nodeIds;

    @NotEmpty
    List<@NotNull @Valid WaypointRequest> waypoints;
}
