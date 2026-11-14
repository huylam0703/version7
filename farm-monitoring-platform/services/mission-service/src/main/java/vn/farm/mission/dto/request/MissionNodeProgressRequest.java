package vn.farm.mission.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.farm.mission.entity.NodeResultStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionNodeProgressRequest {

    @NotNull
    UUID nodeId;

    @NotNull
    NodeResultStatus result;

    @Min(0)
    int recordsCount;

    @Min(0)
    int attempts;

    Instant collectedAt;
}