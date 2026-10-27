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
public class MissionStatusLogResponse {
    UUID logId;

    UUID changedBy;

    String source;

    String oldStatus;

    String newStatus;

    String note;

    Instant createdAt;
}
