
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.response.MissionExecutionProgressResponse;
import uav_gateway_simulator.dto.response.MissionProgressDetailResponse;
import uav_gateway_simulator.dto.MissionResumePlan;

import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionResumePlanner {

    private final MissionClient missionClient;

    public MissionResumePlan createPlan(
            UUID missionId,
            UUID executionId
    ) {
        MissionExecutionProgressResponse execution =
                missionClient.getExecutionProgress(
                        missionId, executionId
                );

        MissionProgressDetailResponse progress =
                missionClient.getMissionProgress(missionId);

        if (!missionId.equals(progress.missionId())
                || !executionId.equals(progress.executionId())
                || !missionId.equals(execution.missionId())
                || !executionId.equals(execution.executionId())) {
            throw new IllegalStateException(
                    "Mission resume progress mismatch"
            );
        }

        if (!"RUNNING".equals(progress.missionStatus())
                || !"EXECUTING".equals(progress.executionStatus())
                || !"EXECUTING".equals(execution.executionStatus())) {
            throw new IllegalStateException(
                    "Mission execution is not resumable"
            );
        }

        if (progress.lastConfirmedSequence()
                != execution.lastConfirmedSequence()) {
            throw new IllegalStateException(
                    "Inconsistent execution progress"
            );
        }

        var waypoints = progress.waypoints();

        if (waypoints == null || waypoints.isEmpty()) {
            throw new IllegalStateException(
                    "Mission has no waypoints"
            );
        }

        var sequences = new HashSet<Short>();
        boolean pendingFound = false;
        Short nextWaypointSeq = null;
        long reachedCount = 0;

        for (var waypoint : waypoints.stream()
                .sorted(Comparator.comparingInt(
                        MissionProgressDetailResponse
                                .WaypointProgress::seq))
                .toList()) {

            if (!sequences.add(waypoint.seq())) {
                throw new IllegalStateException(
                        "Duplicate waypoint sequence"
                );
            }


            if ("REACHED".equals(waypoint.status())) {
                if (pendingFound) {
                    throw new IllegalStateException(
                            "Non-contiguous reached waypoints"
                    );
                }

                reachedCount++;
            }
            else if ("PENDING".equals(waypoint.status())) {
                pendingFound = true;

                if (nextWaypointSeq == null) {
                    nextWaypointSeq = waypoint.seq();
                }
            } else {
                throw new IllegalStateException(
                        "Unsupported waypoint status: "
                                + waypoint.status()
                );
            }
        }


        if (reachedCount != progress.lastConfirmedSequence()
                || reachedCount != progress.reachedWaypoints()
                || progress.totalWaypoints() != waypoints.size()) {
            throw new IllegalStateException(
                    "Inconsistent waypoint progress for resume"
            );
        }


        return new MissionResumePlan(
                missionId,
                executionId,
                progress.lastConfirmedSequence() + 1,
                nextWaypointSeq,
                nextWaypointSeq == null
        );
    }
}
