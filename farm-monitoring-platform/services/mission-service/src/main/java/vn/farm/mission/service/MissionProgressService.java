
package vn.farm.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.MissionNodeProgressRequest;
import vn.farm.mission.dto.request.MissionProgressRequest;
import vn.farm.mission.dto.response.MissionProgressDetailResponse;
import vn.farm.mission.dto.response.MissionProgressResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.repository.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionProgressService {

    private final MissionExecutionRepository executionRepository;
    private final WaypointRepository waypointRepository;
    private final MissionNodeRepository missionNodeRepository;

    // =====================================================
    // POST - UPDATE MISSION PROGRESS (ADMIN / OPERATOR)
    // =====================================================

    @Transactional
    public MissionProgressResponse updateProgress(
            UUID missionId,
            MissionProgressRequest request
    ) {
        MissionExecution execution = executionRepository
                .findForProgress(request.getExecutionId(), missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Mission Execution"
                ));

        return processProgress(missionId, request, execution);
    }

    // =====================================================
    // POST - UPDATE MISSION PROGRESS (UAV SIMULATOR)
    // =====================================================

    @Transactional
    public MissionProgressResponse updateProgress(
            UUID missionId,
            MissionProgressRequest request,
            String clientId
    ) {
        if (clientId == null || clientId.isBlank()) {
            throw BusinessException.invalid(
                    "SIMULATOR_CLIENT_ID_REQUIRED",
                    "Không xác định được Simulator"
            );
        }

        MissionExecution execution = executionRepository
                .findForProgress(request.getExecutionId(), missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Mission Execution"
                ));

        if (!clientId.equals(execution.getAssignedClientId())) {
            throw BusinessException.invalid(
                    "SIMULATOR_NOT_ASSIGNED",
                    "Simulator không được phân công Mission này"
            );
        }

        return processProgress(missionId, request, execution);
    }

    // =====================================================
    // SHARED PROGRESS PROCESSING
    // =====================================================

    private MissionProgressResponse processProgress(
            UUID missionId,
            MissionProgressRequest request,
            MissionExecution execution
    ) {
        Mission mission = execution.getMission();

        // 1. Kiểm tra trạng thái Mission
        if (mission.getStatus() != MissionStatus.RUNNING) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Mission phải ở trạng thái RUNNING"
            );
        }

        // 2. Kiểm tra trạng thái Execution
        if (execution.getStatus()
                != MissionExecutionStatus.EXECUTING) {
            throw BusinessException.invalid(
                    "INVALID_EXECUTION_STATUS",
                    "Execution phải ở trạng thái EXECUTING"
            );
        }

        // 3. Kiểm tra sequence
        long lastSequence = execution.getLastConfirmedSequence();
        long expectedSequence = lastSequence + 1;

        if (request.getSequence() <= lastSequence) {
            throw BusinessException.invalid(
                    "PROGRESS_ALREADY_CONFIRMED",
                    "Progress đã được xác nhận trước đó"
            );
        }

        if (request.getSequence() != expectedSequence) {
            throw BusinessException.invalid(
                    "INVALID_PROGRESS_SEQUENCE",
                    "Sequence không hợp lệ. Expected: "
                            + expectedSequence
            );
        }

        // 4. Kiểm tra Waypoint thuộc Mission
        Waypoint waypoint = waypointRepository
                .findByMission_MissionIdAndSeq(
                        missionId,
                        request.getWaypointSeq()
                )
                .orElseThrow(() -> BusinessException.invalid(
                        "INVALID_WAYPOINT",
                        "Waypoint không thuộc Mission"
                ));

        if ("REACHED".equals(waypoint.getStatus())) {
            throw BusinessException.invalid(
                    "WAYPOINT_ALREADY_REACHED",
                    "Waypoint đã được xác nhận trước đó"
            );
        }

        // 5. Kiểm tra danh sách Node
        List<MissionNodeProgressRequest> nodeResults =
                request.getNodeResults() == null
                        ? List.of()
                        : request.getNodeResults();

        Set<UUID> receivedNodeIds = new HashSet<>();

        // 5.1. Kiểm tra Node trùng
        for (MissionNodeProgressRequest item : nodeResults) {
            if (item == null || item.getNodeId() == null) {
                throw BusinessException.invalid(
                        "INVALID_NODE",
                        "NodeId không được để trống"
                );
            }

            if (!receivedNodeIds.add(item.getNodeId())) {
                throw BusinessException.invalid(
                        "DUPLICATE_NODE",
                        "NodeId bị trùng trong Progress"
                );
            }
        }

        // 5.2. Kiểm tra toàn bộ Node trước khi cập nhật
        List<MissionNode> nodesToUpdate = new ArrayList<>();

        for (MissionNodeProgressRequest item : nodeResults) {
            MissionNode node = missionNodeRepository
                    .findByMission_MissionIdAndNodeId(
                            missionId,
                            item.getNodeId()
                    )
                    .orElseThrow(() -> BusinessException.invalid(
                            "INVALID_NODE",
                            "Node không thuộc Mission"
                    ));

            if (!"PENDING".equals(node.getResult())) {
                throw BusinessException.invalid(
                        "NODE_ALREADY_PROCESSED",
                        "Node đã có kết quả"
                );
            }

            if (item.getResult() == null) {
                throw BusinessException.invalid(
                        "INVALID_NODE_RESULT",
                        "Kết quả Node không được để trống"
                );
            }

            if (item.getRecordsCount() < 0
                    || item.getAttempts() < 0) {
                throw BusinessException.invalid(
                        "INVALID_NODE_PROGRESS",
                        "RecordsCount hoặc Attempts không hợp lệ"
                );
            }

            nodesToUpdate.add(node);
        }

        // 5.3. Cập nhật Node
        for (int i = 0; i < nodesToUpdate.size(); i++) {
            MissionNode node = nodesToUpdate.get(i);
            MissionNodeProgressRequest item = nodeResults.get(i);

            node.setResult(item.getResult().name());
            node.setRecordsCount(item.getRecordsCount());
            node.setAttempts(item.getAttempts());
            node.setCollectedAt(item.getCollectedAt());
        }

        if (!nodesToUpdate.isEmpty()) {
            missionNodeRepository.saveAll(nodesToUpdate);
        }

        // 6. Cập nhật Waypoint
        waypoint.setStatus("REACHED");
        waypoint.setReachedAt(request.getReachedAt());

        waypointRepository.save(waypoint);

        // 7. Xác nhận sequence
        execution.setLastConfirmedSequence(request.getSequence());

        executionRepository.save(execution);

        // 8. Trả response
        return MissionProgressResponse.builder()
                .missionId(missionId)
                .executionId(execution.getExecutionId())
                .executionStatus(execution.getStatus().name())
                .lastConfirmedSequence(
                        execution.getLastConfirmedSequence()
                )
                .waypointSeq(waypoint.getSeq())
                .updatedAt(Instant.now())
                .build();
    }

    // =====================================================
    // GET - MISSION PROGRESS DETAIL
    // =====================================================

    @Transactional(readOnly = true)
    public MissionProgressDetailResponse getProgress(UUID missionId) {

        MissionExecution execution = executionRepository
                .findByMission_MissionId(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Mission chưa có Execution"
                ));

        Mission mission = execution.getMission();

        List<Waypoint> waypoints =
                waypointRepository
                        .findByMission_MissionIdOrderBySeqAsc(missionId);

        List<MissionNode> nodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        int totalWaypoints = waypoints.size();

        int reachedWaypoints = (int) waypoints.stream()
                .filter(w -> "REACHED".equals(w.getStatus()))
                .count();

        int totalNodes = nodes.size();

        int processedNodes = (int) nodes.stream()
                .filter(n ->
                        "SUCCESS".equals(n.getResult())
                                || "FAILED".equals(n.getResult())
                )
                .count();

        double waypointProgress = totalWaypoints == 0
                ? 0.0
                : (double) reachedWaypoints / totalWaypoints;

        double nodeProgress = totalNodes == 0
                ? 0.0
                : (double) processedNodes / totalNodes;

        double progressPercent;

        if (totalWaypoints == 0 && totalNodes == 0) {
            progressPercent = 0.0;
        } else if (totalWaypoints == 0) {
            progressPercent = nodeProgress * 100.0;
        } else if (totalNodes == 0) {
            progressPercent = waypointProgress * 100.0;
        } else {
            progressPercent =
                    (waypointProgress + nodeProgress) * 50.0;
        }

        progressPercent =
                Math.round(progressPercent * 100.0) / 100.0;

        List<MissionProgressDetailResponse.WaypointProgress>
                waypointDetails = waypoints.stream()
                .map(w ->
                        MissionProgressDetailResponse.WaypointProgress
                                .builder()
                                .waypointId(w.getWaypointId())
                                .seq(w.getSeq())
                                .status(w.getStatus())
                                .reachedAt(w.getReachedAt())
                                .build()
                )
                .toList();

        List<MissionProgressDetailResponse.NodeProgress>
                nodeDetails = nodes.stream()
                .map(n ->
                        MissionProgressDetailResponse.NodeProgress
                                .builder()
                                .nodeId(n.getNodeId())
                                .result(n.getResult())
                                .recordsCount(n.getRecordsCount())
                                .attempts(n.getAttempts())
                                .collectedAt(n.getCollectedAt())
                                .build()
                )
                .toList();

        return MissionProgressDetailResponse.builder()
                .missionId(missionId)
                .executionId(execution.getExecutionId())
                .missionStatus(mission.getStatus().name())
                .executionStatus(execution.getStatus().name())
                .lastConfirmedSequence(
                        execution.getLastConfirmedSequence()
                )
                .totalWaypoints(totalWaypoints)
                .reachedWaypoints(reachedWaypoints)
                .totalNodes(totalNodes)
                .processedNodes(processedNodes)
                .progressPercent(progressPercent)
                .waypoints(waypointDetails)
                .nodes(nodeDetails)
                .build();
    }


    @Transactional(readOnly = true)
    public MissionProgressDetailResponse getProgress(
            UUID missionId,
            String clientId
    ) {
        if (clientId == null || clientId.isBlank()) {
            throw BusinessException.invalid(
                    "SIMULATOR_CLIENT_ID_REQUIRED",
                    "Không xác định được Simulator"
            );
        }

        MissionExecution execution = executionRepository
                .findByMission_MissionId(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Mission chưa có Execution"
                ));

        if (!clientId.equals(execution.getAssignedClientId())) {
            throw BusinessException.invalid(
                    "SIMULATOR_NOT_ASSIGNED",
                    "Simulator không được phân công Mission này"
            );
        }

        return getProgress(missionId);
    }

}
