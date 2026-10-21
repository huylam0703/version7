package vn.farm.mission.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import vn.farm.mission.entity.MissionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionDetailResponse {
    UUID missionId;

    UUID farmId;
    UUID uavId;
    UUID gatewayId;

    String name;
    String createdBy;
    String notes;

    MissionStatus status;

    Instant plannedAt;
    Instant startedAt;
    Instant endedAt;

    Integer totalNodes;
    Integer successNodes;
    Integer failedNodes;

    List<WaypointResponse> waypoints;
    List<UUID> nodeIds;
}
