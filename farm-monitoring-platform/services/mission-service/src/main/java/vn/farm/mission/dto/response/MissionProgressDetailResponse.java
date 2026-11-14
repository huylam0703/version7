
package vn.farm.mission.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionProgressDetailResponse {

    UUID missionId;
    UUID executionId;

    String missionStatus;
    String executionStatus;

    long lastConfirmedSequence;

    int totalWaypoints;
    int reachedWaypoints;

    int totalNodes;
    int processedNodes;

    double progressPercent;

    List<WaypointProgress> waypoints;
    List<NodeProgress> nodes;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WaypointProgress {
        UUID waypointId;
        short seq;
        String status;
        Instant reachedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeProgress {
        UUID nodeId;
        String result;
        int recordsCount;
        Integer attempts;
        Instant collectedAt;
    }
}
