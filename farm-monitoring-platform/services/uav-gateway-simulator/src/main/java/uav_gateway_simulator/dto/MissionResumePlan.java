
package uav_gateway_simulator.dto;

import java.util.UUID;

public record MissionResumePlan(
        UUID missionId,
        UUID executionId,
        long nextProgressSequence,
        Short nextWaypointSeq,
        boolean allWaypointsReached
) {}
