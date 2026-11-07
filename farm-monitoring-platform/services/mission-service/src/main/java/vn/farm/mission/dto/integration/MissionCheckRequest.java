package vn.farm.mission.dto.integration;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionCheckRequest {
    UUID farmId;
    UUID uavId;
    UUID gatewayId;
    List<UUID> nodeIds;
}
