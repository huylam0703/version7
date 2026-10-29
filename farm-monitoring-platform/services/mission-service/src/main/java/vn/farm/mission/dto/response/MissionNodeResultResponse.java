package vn.farm.mission.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionNodeResultResponse {
    UUID nodeId;

    String result;

    Integer recordsCount;

    Integer attempts;

    Instant collectedAt;

    String errorCode;

    String errorMessage;
}
