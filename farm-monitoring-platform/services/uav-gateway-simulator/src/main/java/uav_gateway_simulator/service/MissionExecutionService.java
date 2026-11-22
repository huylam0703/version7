
package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;
import uav_gateway_simulator.dto.*;
import uav_gateway_simulator.dto.response.MissionAckResponse;
import uav_gateway_simulator.dto.response.MissionDetailResponse;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.dto.response.WaypointResponse;
import uav_gateway_simulator.exception.MissionCancelledException;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionExecutionService {

    private final MissionClient missionClient;
    private final WaypointSimulationService waypointSimulationService;
    private final MissionHeartbeatScheduler heartbeatScheduler;
    private final MissionResultSimulationService missionResultSimulationService;
    private final MissionExecutionTracker executionTracker;
    private final MissionResumePlanner missionResumePlanner;
    private final MissionRecoveryLeaseManager leaseManager;
    private final MissionCancellationService cancellationService;

    public void prepareMission(MissionReadyPayload payload) {

        // 1. Validate event
        if (payload == null
                || payload.getExecutionId() == null
                || payload.getMissionId() == null
                || payload.getFarmId() == null
                || payload.getUavId() == null
                || payload.getGatewayId() == null
                || payload.getAckDeadline() == null) {

            throw new IllegalArgumentException(
                    "Invalid mission.ready payload"
            );
        }

        UUID missionId = payload.getMissionId();
        UUID executionId = payload.getExecutionId();


        // 2. Check ACK deadline
        if (!Instant.now().isBefore(payload.getAckDeadline())) {
            throw new IllegalStateException(
                    "Mission ACK deadline has expired"
            );
        }

        // 3. Fetch Mission Detail
        MissionDetailResponse mission =
                missionClient.getMissionDetail(missionId);

        if (mission == null) {
            throw new IllegalStateException(
                    "Mission Detail response is empty"
            );
        }

        // 4. Validate Mission information
        if (!Objects.equals(mission.missionId(), missionId)) {
            throw new IllegalStateException(
                    "Mission ID mismatch"
            );
        }

        if (!Objects.equals(mission.farmId(), payload.getFarmId())) {
            throw new IllegalStateException(
                    "Farm ID mismatch"
            );
        }

        if (!Objects.equals(mission.uavId(), payload.getUavId())) {
            throw new IllegalStateException(
                    "UAV ID mismatch"
            );
        }

        if (!Objects.equals(mission.gatewayId(), payload.getGatewayId())) {
            throw new IllegalStateException(
                    "Gateway ID mismatch"
            );
        }

        // 5. Validate Waypoints
        if (mission.waypoints() == null
                || mission.waypoints().isEmpty()) {

            throw new IllegalStateException(
                    "Mission has no waypoints"
            );
        }

        // 6. Validate Node IDs before ACK
        List<UUID> nodeIds = mission.nodeIds();

        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new IllegalStateException(
                    "Mission has no nodes"
            );
        }

        if (nodeIds.stream().anyMatch(Objects::isNull)
                || nodeIds.stream().distinct().count() != nodeIds.size()) {

            throw new IllegalStateException(
                    "Mission contains invalid node IDs"
            );
        }

        log.info(
                "Mission validated successfully: missionId={}, executionId={}, waypoints={}, nodes={}",
                missionId,
                executionId,
                mission.waypoints().size(),
                nodeIds.size()
        );

        // 7. Check ACK deadline again
        if (!Instant.now().isBefore(payload.getAckDeadline())) {
            throw new IllegalStateException(
                    "Mission ACK deadline expired before sending ACK"
            );
        }

        // 8. Send ACK
        cancellationService.checkNotCancelled(missionId);
        MissionAckResponse ackResponse =
                missionClient.ackMission(missionId, executionId);


        // 9. Validate ACK response
        if (ackResponse == null) {
            throw new IllegalStateException(
                    "ACK response is empty"
            );
        }

        if (!Objects.equals(ackResponse.missionId(), missionId)) {
            throw new IllegalStateException(
                    "ACK missionId mismatch"
            );
        }

        if (!Objects.equals(ackResponse.executionId(), executionId)) {
            throw new IllegalStateException(
                    "ACK executionId mismatch"
            );
        }

        if (ackResponse.acknowledgedAt() == null) {
            throw new IllegalStateException(
                    "ACK response missing acknowledgedAt"
            );
        }

        if (!"EXECUTING".equals(ackResponse.status())) {
            throw new IllegalStateException(
                    "Mission is not in EXECUTING status"
            );
        }

        log.info(
                "Mission ACK successful: missionId={}, executionId={}, status={}",
                missionId,
                executionId,
                ackResponse.status()
        );

        try {

            // 10. Start Heartbeat
            heartbeatScheduler.start(missionId, executionId);

            log.info(
                    "Heartbeat started: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

            // 11. Simulate Waypoints
            log.info(
                    "Starting waypoint simulation: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

            waypointSimulationService.simulate(
                    missionId,
                    executionId,
                    mission.waypoints()
            );

            log.info(
                    "Waypoint simulation completed: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

            cancellationService.checkNotCancelled(missionId);
            // 12. Mark Results Pending
            // Normal mission execution does not require a recovery lease.
            executionTracker.markResultsPending(executionId);

            log.info(
                    "Mission Results pending: missionId={}, executionId={}",
                    missionId,
                    executionId
            );


            // 13. Send Mission Results
            try {

                log.info(
                        "Sending Mission Results: missionId={}, executionId={}, totalNodes={}",
                        missionId,
                        executionId,
                        nodeIds.size()
                );

                cancellationService.checkNotCancelled(missionId);

                MissionResultResponse resultResponse =
                        missionResultSimulationService.simulateResults(
                                missionId,
                                executionId,
                                nodeIds
                        );

                // 14. Validate Results response
                if (resultResponse == null) {
                    throw new IllegalStateException(
                            "Mission Results response is empty"
                    );
                }

                if (!Objects.equals(
                        resultResponse.missionId(),
                        missionId)) {

                    throw new IllegalStateException(
                            "Mission Results missionId mismatch"
                    );
                }

                if (!Objects.equals(
                        resultResponse.executionId(),
                        executionId)) {

                    throw new IllegalStateException(
                            "Mission Results executionId mismatch"
                    );
                }


                String finalStatus = resultResponse.status();

                if (!"COMPLETED".equals(finalStatus)
                        && !"PARTIAL".equals(finalStatus)
                        && !"FAILED".equals(finalStatus)) {

                    throw new IllegalStateException(
                            "Invalid final Mission status: " + finalStatus
                    );
                }


                log.info(
                        "Mission completed successfully: missionId={}, executionId={}, status={}, successNodes={}, failedNodes={}",
                        missionId,
                        executionId,
                        resultResponse.status(),
                        resultResponse.successNodes(),
                        resultResponse.failedNodes()
                );

            }
            catch (MissionCancelledException e) {
                log.info(
                        "Mission Results skipped because mission was cancelled: missionId={}, executionId={}",
                        missionId,
                        executionId
                );
                throw e;

            }
            catch (Exception e) {

                // Results failed, preserve retry state.
                // MissionProcessingService will not restart Waypoints.
                executionTracker.markResultsRetry(executionId);

                log.error(
                        "Mission Results failed, retry required: missionId={}, executionId={}",
                        missionId,
                        executionId,
                        e
                );

                throw e;
            }

        } finally {

            // 15. Always stop Heartbeat
            heartbeatScheduler.stop(executionId);

            log.info(
                    "Heartbeat stopped: missionId={}, executionId={}",
                    missionId,
                    executionId
            );
        }
    }


    public void resumeMission(MissionReadyPayload payload) {
        if (payload == null
                || payload.getMissionId() == null
                || payload.getExecutionId() == null
                || payload.getFarmId() == null
                || payload.getUavId() == null
                || payload.getGatewayId() == null) {
            throw new IllegalArgumentException(
                    "Invalid mission resume payload"
            );
        }

        UUID missionId = payload.getMissionId();
        UUID executionId = payload.getExecutionId();

        leaseManager.requireValidLease(executionId);

        MissionResumePlan plan =
                missionResumePlanner.createPlan(missionId, executionId);

        MissionDetailResponse mission =
                missionClient.getMissionDetail(missionId);

        if (mission == null
                || !Objects.equals(mission.missionId(), missionId)
                || !Objects.equals(mission.farmId(), payload.getFarmId())
                || !Objects.equals(mission.uavId(), payload.getUavId())
                || !Objects.equals(mission.gatewayId(), payload.getGatewayId())
                || mission.waypoints() == null
                || mission.waypoints().isEmpty()
                || mission.nodeIds() == null
                || mission.nodeIds().isEmpty()
                || mission.nodeIds().stream().anyMatch(Objects::isNull)
                || mission.nodeIds().stream().distinct().count()
                != mission.nodeIds().size()) {
            throw new IllegalStateException(
                    "Invalid mission detail for resume"
            );
        }

        // Resume must not send ACK again.
        try {
            heartbeatScheduler.start(missionId, executionId);


            MissionResumePlan latestPlan =
                    missionResumePlanner.createPlan(
                            missionId,
                            executionId
                    );

            List<WaypointResponse> sortedWaypoints =
                    mission.waypoints().stream()
                            .sorted(Comparator.comparingInt(
                                    WaypointResponse::seq
                            ))
                            .toList();

            if (sortedWaypoints.isEmpty()) {
                throw new IllegalStateException(
                        "Mission has no waypoints"
                );
            }

            Set<Integer> waypointSequences = new HashSet<>();

            for (int i = 0; i < sortedWaypoints.size(); i++) {
                int seq = sortedWaypoints.get(i).seq();

                if (!waypointSequences.add(seq)) {
                    throw new IllegalStateException(
                            "Duplicate waypoint sequence: " + seq
                    );
                }

                if (seq != i + 1) {
                    throw new IllegalStateException(
                            "Invalid waypoint sequence: " + seq
                    );
                }
            }

            if (latestPlan.allWaypointsReached()) {
                if (latestPlan.nextWaypointSeq() != null
                        || latestPlan.nextProgressSequence()
                        != sortedWaypoints.size() + 1L) {
                    throw new IllegalStateException(
                            "Inconsistent completed waypoint progress"
                    );
                }
            } else {
                if (latestPlan.nextWaypointSeq() == null
                        || latestPlan.nextWaypointSeq().intValue()
                        != latestPlan.nextProgressSequence()
                        || latestPlan.nextProgressSequence()
                        > sortedWaypoints.size()) {
                    throw new IllegalStateException(
                            "Inconsistent resume waypoint sequence"
                    );
                }
            }


            if (!plan.equals(latestPlan)) {
                throw new IllegalStateException(
                        "Mission progress changed before resume: "
                                + executionId
                );
            }

            waypointSimulationService.simulateResume(
                    missionId,
                    executionId,
                    sortedWaypoints,
                    latestPlan
            );

            // Waypoints completed. Recovery ownership must still be valid.
            leaseManager.requireValidLease(executionId);
            cancellationService.checkNotCancelled(missionId);
            executionTracker.markResultsPending(executionId);

            try {
                cancellationService.checkNotCancelled(missionId);
                MissionResultResponse resultResponse =
                        missionResultSimulationService.simulateResults(
                                missionId,
                                executionId,
                                mission.nodeIds()
                        );

                if (resultResponse == null
                        || !Objects.equals(resultResponse.missionId(), missionId)
                        || !Objects.equals(resultResponse.executionId(), executionId)
                        || (!"COMPLETED".equals(resultResponse.status())
                        && !"PARTIAL".equals(resultResponse.status())
                        && !"FAILED".equals(resultResponse.status()))) {
                    throw new IllegalStateException(
                            "Invalid resume Mission Results response"
                    );
                }

            }
            catch (MissionCancelledException e) {
                log.info(
                        "Recovery Results skipped because mission was cancelled: missionId={}, executionId={}",
                        missionId,
                        executionId
                );
                throw e;

            }
            catch (RecoveryOwnershipLostException e) {

                executionTracker.failDueToOwnershipLoss(executionId);

                log.error(
                        "Recovery Results stopped due to ownership loss: executionId={}",
                        executionId,
                        e
                );

                throw e;

            } catch (Exception e) {

                executionTracker.markResultsRetry(executionId);

                log.error(
                        "Mission Results failed, retry required: missionId={}, executionId={}",
                        missionId,
                        executionId,
                        e
                );

                throw e;
            }

        } finally {
            heartbeatScheduler.stop(executionId);
        }
    }

}
