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
public class MissionNodeResultRequest {
    @NotNull
    UUID nodeId;

    @NotNull
    NodeResultStatus result;

    @NotNull
    @Min(0)
    Integer recordsCount;

    @NotNull
    @Min(0)
    Integer attempts;

    Instant collectedAt;

    String errorCode;
    String errorMessage;
}
