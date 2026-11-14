package vn.farm.mission.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class MissionResponse {
    UUID missionId;
    UUID farmId;
    UUID uavId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    UUID executionId;
    UUID gatewayId;
    UUID createdBy;
    String name;
    String status;
    Instant plannedAt;
    int totalNodes;
    List<UUID> nodeIds;
    List<WaypointResponse> waypoints;
}
