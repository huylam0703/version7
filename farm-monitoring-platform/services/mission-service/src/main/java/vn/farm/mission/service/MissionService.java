package vn.farm.mission.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.event.MissionReadyPayload;
import vn.farm.mission.dto.request.*;
import vn.farm.mission.dto.response.*;
import vn.farm.mission.entity.*;
import vn.farm.mission.mapper.MissionMapper;
import vn.farm.mission.outbox.MissionOutboxService;
import vn.farm.mission.repository.*;
import vn.farm.mission.validation.MissionReferenceValidator;
import vn.farm.starter.security.CurrentUser;
import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
import vn.farm.core.FarmEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import vn.farm.mission.config.SimulatorProperties;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService {
    final MissionRepository missionRepository;
    final WaypointRepository waypointRepository;
    final MissionNodeRepository missionNodeRepository;
    final MissionStatusLogRepository missionStatusLogRepository;
    final MissionMapper missionMapper;
    final CurrentUser currentUser;
    final MissionReferenceValidator references;
    final MissionExecutionRepository missionExecutionRepository;
    final MissionOutboxService missionOutboxService;
    final SimulatorProperties simulatorProperties;

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request) {
        if(new HashSet<>(request.getNodeIds()).size() != request.getNodeIds().size()) {
            throw BusinessException.invalid("DUPLICATE_NOTE", "nodeIds khong duoc trung nhau");
        }
        Set<Integer> sequences = new HashSet<>();

        for (WaypointRequest w : request.getWaypoints()) {
            if(!sequences.add(w.getSeq())){
                throw BusinessException.invalid("DUPLICATE_WAYPOINT_SEQ","Seq của waypoint không được trùng");
            }
            if(w.getAltitudeM() != null && w.getAltitudeM().scale()>2){
                throw BusinessException.invalid("INVALID_ALTITUDE","altitudeM tối đa 2 chữ số thập phân");
            }
        }
        references.validate(request);
        Mission mission = missionMapper.toMission(request);
        mission.setCreatedBy(currentUser.id());
        mission.setStatus(MissionStatus.DRAFT);
        mission.setTotalNodes(request.getNodeIds().size());
        mission = missionRepository.save(mission);

        List<Waypoint> saveWaypoints = new ArrayList<>();
        for (WaypointRequest w : request.getWaypoints()){
            Waypoint wp = new Waypoint();
            wp.setMission(mission);
            wp.setSeq(w.getSeq().shortValue());
            wp.setLat(w.getLat());
            wp.setLng(w.getLng());
            wp.setAltM(w.getAltitudeM() == null ? BigDecimal.ZERO : w.getAltitudeM());
            saveWaypoints.add(waypointRepository.save(wp));
        }

        for (UUID nodeId : request.getNodeIds()){
            MissionNode node = new MissionNode();
            node.setMission(mission);
            node.setNodeId(nodeId);
            missionNodeRepository.save(node);
        }

        createMissionStatusLog(
                mission,
                "",
                "DRAFT",
                mission.getCreatedBy(),
                "SYSTEM",
                "Mission create"
        );

        List<WaypointResponse> wpResponse = saveWaypoints.stream()
                .map(waypoint -> new WaypointResponse(waypoint.getWaypointId(),
                        waypoint.getSeq(),
                        waypoint.getLat(),
                        waypoint.getLng(),
                        waypoint.getAltM(),
                        waypoint.getStatus()
                        ))
                .toList();


        return MissionResponse.builder()
                .missionId(mission.getMissionId())
                .farmId(mission.getFarmId())
                .uavId(mission.getUavId())
                .gatewayId(mission.getGatewayId())
                .createdBy(mission.getCreatedBy())
                .name(mission.getName())
                .status(mission.getStatus().name())
                .plannedAt(mission.getPlannedAt())
                .totalNodes(mission.getTotalNodes())
                .nodeIds(List.copyOf(request.getNodeIds()))
                .waypoints(wpResponse)
                .build();

    }


    @Transactional(readOnly = true)
    public MissionDetailResponse getMissionById(
            UUID missionId,
            String clientId
    ) {
        if (clientId == null || clientId.isBlank()) {
            throw BusinessException.invalid(
                    "SIMULATOR_CLIENT_ID_REQUIRED",
                    "Không xác định được Simulator"
            );
        }

        MissionExecution execution = missionExecutionRepository
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

        return getMissionById(missionId);
    }


    @Transactional(readOnly = true)
    public MissionDetailResponse getMissionById(UUID missionId) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() ->
                        BusinessException.notFound(
                                "MISSION_NOT_FOUND",
                                "Không tìm thấy Mission"
                        )
                );

        List<Waypoint> waypoints =
                waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId);

        List<MissionNode> missionNodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        MissionDetailResponse response =
                missionMapper.toMissionDetailResponse(mission);

        response.setWaypoints(
                waypoints.stream()
                        .map(missionMapper::toWaypointResponse)
                        .toList()
        );

        response.setNodeIds(
                missionNodes.stream()
                        .map(MissionNode::getNodeId)
                        .toList()
        );

        return response;
    }

    @Transactional(readOnly = true)
    public Page<MissionResponse> getAllMissions(
            int page,
            int size,
            MissionStatus status
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Specification<Mission> specification =
                (root, query, cb) ->
                        status == null
                                ? cb.conjunction()
                                : cb.equal(root.get("status"), status);

        return missionRepository
                .findAll(specification, pageable)
                .map(missionMapper::toMissionResponse);
    }

    @Transactional
    public MissionResponse updateMission(
            UUID missionId,
            UpdateMissionRequest request
    ) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        if (mission.getStatus() != MissionStatus.DRAFT
                && mission.getStatus() != MissionStatus.PLANNED) {
            throw BusinessException.invalid(
                    "MISSION_CANNOT_UPDATE",
                    "Không thể cập nhật Mission đã bắt đầu"
            );
        }

        mission.setName(request.getName());
        mission.setNotes(request.getNotes());
        mission.setPlannedAt(request.getPlannedAt());

        Mission saved = missionRepository.save(mission);

        return missionMapper.toMissionResponse(saved);
    }

    @Transactional
    public MissionResponse startMission(UUID missionId) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        if (mission.getStatus() != MissionStatus.PLANNED) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Chỉ Mission ở trạng thái PLANNED mới được bắt đầu"
            );
        }

        if (missionExecutionRepository.existsByMission_MissionId(missionId)) {
            throw BusinessException.invalid(
                    "MISSION_ALREADY_DISPATCHED",
                    "Mission đã có Execution"
            );
        }

        MissionStatus oldStatus = mission.getStatus();
        Instant now = Instant.now();

        mission.setStatus(MissionStatus.RUNNING);
        mission.setStartedAt(now);

        Mission saved = missionRepository.save(mission);

        MissionExecution execution = MissionExecution.builder()
                .mission(saved)
                .status(MissionExecutionStatus.WAITING_ACK)
                .assignedClientId(simulatorProperties.getClientId())
                .startedAt(now)
                .ackDeadline(now.plusSeconds(90))
                .notificationAttempts(1)
                .lastConfirmedSequence(0L)
                .build();

        execution = missionExecutionRepository.save(execution);

        MissionReadyPayload payload = new MissionReadyPayload(
                execution.getExecutionId(),
                saved.getMissionId(),
                saved.getFarmId(),
                saved.getUavId(),
                saved.getGatewayId(),
                execution.getAckDeadline()
        );

        missionOutboxService.saveEvent(
                FarmEvents.MISSION_READY,
                payload
        );

        createMissionStatusLog(
                mission,
                oldStatus.name(),
                "RUNNING",
                mission.getCreatedBy(),
                "OPERATOR",
                "Mission running"
        );

        publishMissionStatusChanged(mission, oldStatus);

        MissionResponse response = buildMissionResponse(saved);
        response.setExecutionId(execution.getExecutionId());

        return response;
    }

    @Transactional
    public MissionResponse cancelMission(UUID missionId) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        MissionStatus status = mission.getStatus();

        if (status == MissionStatus.COMPLETED
                || status == MissionStatus.PARTIAL
                || status == MissionStatus.FAILED
                || status == MissionStatus.CANCELLED) {

            throw BusinessException.invalid(
                    "MISSION_CANNOT_CANCEL",
                    "Mission đã kết thúc, không thể hủy"
            );
        }
        MissionStatus oldStatus = mission.getStatus();
        mission.setStatus(MissionStatus.CANCELLED);
        mission.setEndedAt(Instant.now());

        Mission saved = missionRepository.save(mission);

        missionExecutionRepository.findByMission_MissionId(missionId)
                .ifPresent(execution -> {

                    if (execution.getStatus() != MissionExecutionStatus.COMPLETED
                            && execution.getStatus() != MissionExecutionStatus.FAILED
                            && execution.getStatus() != MissionExecutionStatus.CANCELLED) {

                        execution.setStatus(MissionExecutionStatus.CANCELLED);

                        missionExecutionRepository.save(execution);
                    }
                });

        // Cần lưu MissionStatusLog tại đây.
        createMissionStatusLog(
                mission,
                oldStatus.name(),
                "CANCELLED",
                mission.getCreatedBy(),
                "OPERATOR",
                "Mission cancelled"
        );

        publishMissionStatusChanged(saved, oldStatus);

        return missionMapper.toMissionResponse(saved);
    }

    @Transactional
    public MissionResponse receiveMissionResults(
            UUID missionId,
            MissionResultRequest request
    ) {
        return processMissionResults(missionId, request);
    }
    @Transactional
    public MissionResponse receiveMissionResults(
            UUID missionId,
            MissionResultRequest request,
            String clientId
    ) {
        if (clientId == null || clientId.isBlank()) {
            throw BusinessException.invalid(
                    "SIMULATOR_CLIENT_ID_REQUIRED",
                    "Không xác định được Simulator"
            );
        }

        if (request.getExecutionId() == null) {
            throw BusinessException.invalid(
                    "EXECUTION_ID_REQUIRED",
                    "ExecutionId không được để trống"
            );
        }

        MissionExecution execution =
                missionExecutionRepository.findForProgress(
                        request.getExecutionId(),
                        missionId
                ).orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Execution thuộc Mission"
                ));

        if (!clientId.equals(execution.getAssignedClientId())) {
            throw BusinessException.invalid(
                    "SIMULATOR_NOT_ASSIGNED",
                    "Simulator không được phân công Mission này"
            );
        }

        return processMissionResults(missionId, request);
    }
    private MissionResponse processMissionResults(
            UUID missionId,
            MissionResultRequest request
    ) {
        // Giữ nguyên logic xử lý Mission Results hiện tại,
        // bắt đầu từ missionRepository.findForResults(...)

        Mission mission = missionRepository.findForResults(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        if (mission.getStatus() == MissionStatus.COMPLETED
                || mission.getStatus() == MissionStatus.PARTIAL
                || mission.getStatus() == MissionStatus.FAILED) {

            List<MissionNode> existingNodes =
                    missionNodeRepository.findByMission_MissionId(missionId);

            if (!isSameMissionResults(existingNodes, request)) {
                throw BusinessException.invalid(
                        "MISSION_RESULT_CONFLICT",
                        "Kết quả gửi lại không khớp với kết quả đã lưu"
                );
            }

            MissionResponse response = buildMissionResponse(mission);

            MissionExecution existingExecution =
                    missionExecutionRepository.findByMission_MissionId(missionId)
                            .orElseThrow(() -> BusinessException.notFound(
                                    "EXECUTION_NOT_FOUND",
                                    "Không tìm thấy MissionExecution"
                            ));

            response.setExecutionId(existingExecution.getExecutionId());
            response.setSuccessNodes(mission.getSuccessNodes());
            response.setFailedNodes(mission.getFailedNodes());

            return response;
        }

        if (mission.getStatus() != MissionStatus.RUNNING) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Mission phải ở trạng thái RUNNING"
            );
        }

        if (!Boolean.TRUE.equals(request.getCompleted())) {
            throw BusinessException.invalid(
                    "PARTIAL_RESULTS_NOT_SUPPORTED",
                    "Phiên bản này chỉ nhận báo cáo cuối cùng"
            );
        }

        List<MissionNode> missionNodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        Map<UUID, MissionNode> nodeMap = missionNodes.stream()
                .collect(Collectors.toMap(
                        MissionNode::getNodeId,
                        Function.identity()
                ));

        Set<UUID> receivedNodeIds = new HashSet<>();

        int successCount = 0;
        int failedCount = 0;

        for (MissionNodeResultRequest item : request.getResults()) {

            if (!receivedNodeIds.add(item.getNodeId())) {
                throw BusinessException.invalid(
                        "DUPLICATE_NODE_RESULT",
                        "NodeId bị trùng trong kết quả"
                );
            }
            if (item.getResult() == NodeResultStatus.SUCCESS
                    && item.getRecordsCount() == 0) {
                throw BusinessException.invalid(
                        "INVALID_SUCCESS_RESULT",
                        "Node SUCCESS phải có ít nhất một bản ghi"
                );
            }

            if (item.getResult() == NodeResultStatus.FAILED
                    && item.getRecordsCount() > 0) {
                throw BusinessException.invalid(
                        "INVALID_FAILED_RESULT",
                        "Node FAILED không được có bản ghi thành công"
                );
            }

            MissionNode node = nodeMap.get(item.getNodeId());

            if (node == null) {
                throw BusinessException.invalid(
                        "INVALID_NODE",
                        "Node không thuộc Mission"
                );
            }

            String currentResult = node.getResult();
            String incomingResult = item.getResult().name();

            if (currentResult != null
                    && !"PENDING".equals(currentResult)
                    && !currentResult.equals(incomingResult)) {

                throw BusinessException.invalid(
                        "NODE_RESULT_CONFLICT",
                        "Kết quả cuối cùng của Node không khớp với Progress"
                );
            }


            node.setResult(item.getResult().name());
            node.setRecordsCount(item.getRecordsCount());
            node.setAttempts(item.getAttempts());
            node.setCollectedAt(item.getCollectedAt());
            node.setErrorCode(item.getErrorCode());
            node.setErrorMessage(item.getErrorMessage());

            if (item.getResult() == NodeResultStatus.SUCCESS) {
                successCount++;
            } else {
                failedCount++;
            }
        }

        if (receivedNodeIds.size() != missionNodes.size()) {
            throw BusinessException.invalid(
                    "INCOMPLETE_MISSION_RESULTS",
                    "Báo cáo cuối cùng phải có kết quả cho tất cả Node"
            );
        }

        missionNodeRepository.saveAll(missionNodes);

        mission.setSuccessNodes(successCount);
        mission.setFailedNodes(failedCount);

        MissionStatus oldStatus1 = mission.getStatus();

        if (failedCount == 0) {
            String oldStatus = String.valueOf(mission.getStatus());

            mission.setStatus(MissionStatus.COMPLETED);

            createMissionStatusLog(
                    mission,
                    oldStatus,
                    "COMPLETED",
                    mission.getCreatedBy(),
                    "OPERATOR",
                    "Mission completed"
            );
        } else if (successCount == 0) {
            String oldStatus = String.valueOf(mission.getStatus());

            mission.setStatus(MissionStatus.FAILED);

            createMissionStatusLog(
                    mission,
                    oldStatus,
                    "FAILED",
                    mission.getCreatedBy(),
                    "OPERATOR",
                    "Mission failed"
            );
        } else {
            String oldStatus = String.valueOf(mission.getStatus());

            mission.setStatus(MissionStatus.PARTIAL);

            createMissionStatusLog(
                    mission,
                    oldStatus,
                    "PARTIAL",
                    mission.getCreatedBy(),
                    "OPERATOR",
                    "Mission partial"
            );
        }


        Instant finishedAt = Instant.now();
        mission.setEndedAt(finishedAt);

        MissionExecution execution = missionExecutionRepository
                .findByMission_MissionId(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy MissionExecution"
                ));

        if (execution.getStatus() != MissionExecutionStatus.EXECUTING) {
            throw BusinessException.invalid(
                    "INVALID_EXECUTION_STATUS",
                    "Execution phải ở trạng thái EXECUTING"
            );
        }

        MissionExecutionStatus executionStatus =
                mission.getStatus() == MissionStatus.FAILED
                        ? MissionExecutionStatus.FAILED
                        : MissionExecutionStatus.COMPLETED;

        execution.setStatus(executionStatus);
        missionExecutionRepository.save(execution);

        Mission saved = missionRepository.save(mission);

        publishMissionStatusChanged(saved, oldStatus1);


        // Cần lưu MissionStatusLog khi trạng thái thay đổi.

        MissionResponse response = missionMapper.toMissionResponse(saved);
        response.setExecutionId(execution.getExecutionId());
        response.setSuccessNodes(successCount);
        response.setFailedNodes(failedCount);

        List<Waypoint> waypoints =
                waypointRepository.findByMission_MissionIdOrderBySeqAsc(
                        saved.getMissionId()
                );

        response.setWaypoints(
                waypoints.stream()
                        .map(missionMapper::toWaypointResponse)
                        .toList()
        );

        response.setNodeIds(
                missionNodes.stream()
                        .map(MissionNode::getNodeId)
                        .toList()
        );

        return response;
    }

//    @Transactional
//    public MissionResponse receiveMissionResults(
//            UUID missionId,
//            MissionResultRequest request,
//            String clientId
//    ) {
//        if (request.getExecutionId() == null) {
//            throw BusinessException.invalid(
//                    "EXECUTION_ID_REQUIRED",
//                    "ExecutionId không được để trống"
//            );
//        }
//
//        MissionExecution execution =
//                missionExecutionRepository.findForProgress(
//                        request.getExecutionId(),
//                        missionId
//                ).orElseThrow(() -> BusinessException.notFound(
//                        "EXECUTION_NOT_FOUND",
//                        "Không tìm thấy Execution thuộc Mission"
//                ));
//
//        if (clientId == null
//                || execution.getAssignedClientId() == null
//                || !clientId.equals(execution.getAssignedClientId())) {
//            throw BusinessException.invalid(
//                    "SIMULATOR_NOT_ASSIGNED",
//                    "Simulator không được phân công Mission này"
//            );
//        }
//
//        Mission mission = missionRepository.findForResults(missionId)
//                .orElseThrow(() -> BusinessException.notFound(
//                        "MISSION_NOT_FOUND",
//                        "Mission không tồn tại"
//                ));
//
//        if (mission.getStatus() == MissionStatus.COMPLETED
//                || mission.getStatus() == MissionStatus.PARTIAL
//                || mission.getStatus() == MissionStatus.FAILED) {
//
//            List<MissionNode> existingNodes =
//                    missionNodeRepository.findByMission_MissionId(missionId);
//
//            if (!isSameMissionResults(existingNodes, request)) {
//                throw BusinessException.invalid(
//                        "MISSION_RESULT_CONFLICT",
//                        "Kết quả gửi lại không khớp với kết quả đã lưu"
//                );
//            }
//
//            return buildMissionResponse(mission);
//        }
//
//        if (mission.getStatus() != MissionStatus.RUNNING) {
//            throw BusinessException.invalid(
//                    "INVALID_MISSION_STATUS",
//                    "Mission phải ở trạng thái RUNNING"
//            );
//        }
//
//        if (!Boolean.TRUE.equals(request.getCompleted())) {
//            throw BusinessException.invalid(
//                    "PARTIAL_RESULTS_NOT_SUPPORTED",
//                    "Phiên bản này chỉ nhận báo cáo cuối cùng"
//            );
//        }
//
//        List<MissionNode> missionNodes =
//                missionNodeRepository.findByMission_MissionId(missionId);
//
//        Map<UUID, MissionNode> nodeMap = missionNodes.stream()
//                .collect(Collectors.toMap(
//                        MissionNode::getNodeId,
//                        Function.identity()
//                ));
//
//        Set<UUID> receivedNodeIds = new HashSet<>();
//
//        int successCount = 0;
//        int failedCount = 0;
//
//        for (MissionNodeResultRequest item : request.getResults()) {
//
//            if (!receivedNodeIds.add(item.getNodeId())) {
//                throw BusinessException.invalid(
//                        "DUPLICATE_NODE_RESULT",
//                        "NodeId bị trùng trong kết quả"
//                );
//            }
//            if (item.getResult() == NodeResultStatus.SUCCESS
//                    && item.getRecordsCount() == 0) {
//                throw BusinessException.invalid(
//                        "INVALID_SUCCESS_RESULT",
//                        "Node SUCCESS phải có ít nhất một bản ghi"
//                );
//            }
//
//            if (item.getResult() == NodeResultStatus.FAILED
//                    && item.getRecordsCount() > 0) {
//                throw BusinessException.invalid(
//                        "INVALID_FAILED_RESULT",
//                        "Node FAILED không được có bản ghi thành công"
//                );
//            }
//
//            MissionNode node = nodeMap.get(item.getNodeId());
//
//            if (node == null) {
//                throw BusinessException.invalid(
//                        "INVALID_NODE",
//                        "Node không thuộc Mission"
//                );
//            }
//
//            String currentResult = node.getResult();
//            String incomingResult = item.getResult().name();
//
//            if (currentResult != null
//                    && !"PENDING".equals(currentResult)
//                    && !currentResult.equals(incomingResult)) {
//
//                throw BusinessException.invalid(
//                        "NODE_RESULT_CONFLICT",
//                        "Kết quả cuối cùng của Node không khớp với Progress"
//                );
//            }
//
//
//            node.setResult(item.getResult().name());
//            node.setRecordsCount(item.getRecordsCount());
//            node.setAttempts(item.getAttempts());
//            node.setCollectedAt(item.getCollectedAt());
//            node.setErrorCode(item.getErrorCode());
//            node.setErrorMessage(item.getErrorMessage());
//
//            if (item.getResult() == NodeResultStatus.SUCCESS) {
//                successCount++;
//            } else {
//                failedCount++;
//            }
//        }
//
//        if (receivedNodeIds.size() != missionNodes.size()) {
//            throw BusinessException.invalid(
//                    "INCOMPLETE_MISSION_RESULTS",
//                    "Báo cáo cuối cùng phải có kết quả cho tất cả Node"
//            );
//        }
//
//        missionNodeRepository.saveAll(missionNodes);
//
//        mission.setSuccessNodes(successCount);
//        mission.setFailedNodes(failedCount);
//
//        MissionStatus oldStatus1 = mission.getStatus();
//
//        if (failedCount == 0) {
//            String oldStatus = String.valueOf(mission.getStatus());
//
//            mission.setStatus(MissionStatus.COMPLETED);
//
//            createMissionStatusLog(
//                    mission,
//                    oldStatus,
//                    "COMPLETED",
//                    mission.getCreatedBy(),
//                    "OPERATOR",
//                    "Mission completed"
//            );
//        } else if (successCount == 0) {
//            String oldStatus = String.valueOf(mission.getStatus());
//
//            mission.setStatus(MissionStatus.FAILED);
//
//            createMissionStatusLog(
//                    mission,
//                    oldStatus,
//                    "FAILED",
//                    mission.getCreatedBy(),
//                    "OPERATOR",
//                    "Mission failed"
//            );
//        } else {
//            String oldStatus = String.valueOf(mission.getStatus());
//
//            mission.setStatus(MissionStatus.PARTIAL);
//
//            createMissionStatusLog(
//                    mission,
//                    oldStatus,
//                    "PARTIAL",
//                    mission.getCreatedBy(),
//                    "OPERATOR",
//                    "Mission partial"
//            );
//        }
//
//
//        Instant finishedAt = Instant.now();
//        mission.setEndedAt(finishedAt);
//
//        if (execution.getStatus() != MissionExecutionStatus.EXECUTING) {
//            throw BusinessException.invalid(
//                    "INVALID_EXECUTION_STATUS",
//                    "Execution phải ở trạng thái EXECUTING"
//            );
//        }
//
//        MissionExecutionStatus executionStatus =
//                mission.getStatus() == MissionStatus.FAILED
//                        ? MissionExecutionStatus.FAILED
//                        : MissionExecutionStatus.COMPLETED;
//
//        execution.setStatus(executionStatus);
//        missionExecutionRepository.save(execution);
//
//        Mission saved = missionRepository.save(mission);
//
//        publishMissionStatusChanged(saved, oldStatus1);
//
//
//        // Cần lưu MissionStatusLog khi trạng thái thay đổi.
//
//        MissionResponse response = missionMapper.toMissionResponse(saved);
//
//        List<Waypoint> waypoints =
//                waypointRepository.findByMission_MissionIdOrderBySeqAsc(
//                        saved.getMissionId()
//                );
//
//        response.setWaypoints(
//                waypoints.stream()
//                        .map(missionMapper::toWaypointResponse)
//                        .toList()
//        );
//
//        response.setNodeIds(
//                missionNodes.stream()
//                        .map(MissionNode::getNodeId)
//                        .toList()
//        );
//
//        return response;
//    }

    @Transactional
    public MissionResponse planMission(UUID missionId) {

        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        if (mission.getStatus() != MissionStatus.DRAFT) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Chỉ Mission ở trạng thái DRAFT mới được lập kế hoạch"
            );
        }

        List<Waypoint> waypoints =
                waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId);

        if (waypoints.isEmpty()) {
            throw BusinessException.invalid(
                    "WAYPOINT_REQUIRED",
                    "Mission phải có ít nhất một Waypoint"
            );
        }

        List<MissionNode> missionNodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        if (missionNodes.isEmpty()) {
            throw BusinessException.invalid(
                    "NODE_REQUIRED",
                    "Mission phải có ít nhất một Sensor Node"
            );
        }
        MissionStatus oldStatus = mission.getStatus();
        mission.setStatus(MissionStatus.PLANNED);

        Mission saved = missionRepository.save(mission);

        createMissionStatusLog(
                mission,
                oldStatus.name(),
                "PLANNED",
                mission.getCreatedBy(),
                "OPERATOR",
                "Mission planned"
        );

        publishMissionStatusChanged(saved, oldStatus);

        return buildMissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MissionNodeResultResponse> getMissionResults(UUID missionId) {

        missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound("MISSION_NOT_FOUND",
                        "Mission not found"));

        List<MissionNode> missionNodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        return missionNodes.stream()
                .map(node -> MissionNodeResultResponse.builder()
                        .nodeId(node.getNodeId())
                        .result(node.getResult())
                        .recordsCount(node.getRecordsCount())
                        .attempts(
                                node.getAttempts() == null
                                        ? null
                                        : node.getAttempts().intValue()
                        )
                        .collectedAt(node.getCollectedAt())
                        .errorCode(node.getErrorCode())
                        .errorMessage(node.getErrorMessage())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MissionStatusLogResponse> getMissionStatusHistory(
            UUID missionId) {

        missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

        List<MissionStatusLog> logs =
                missionStatusLogRepository
                        .findByMission_MissionIdOrderByCreatedAtAsc(missionId);

        return logs.stream()
                .map(log -> MissionStatusLogResponse.builder()
                        .logId(log.getLogId())
                        .changedBy(log.getChangedBy())
                        .source(log.getSource())
                        .oldStatus(log.getOldStatus())
                        .newStatus(log.getNewStatus())
                        .note(log.getNote())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();
    }

    public void createMissionStatusLog(
            Mission mission,
            String oldStatus,
            String newStatus,
            UUID changedBy,
            String source,
            String note
    ) {
        MissionStatusLog log = MissionStatusLog.builder()
                .mission(mission)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .source(source)
                .note(note)
                .createdAt(Instant.now())
                .build();

        missionStatusLogRepository.save(log);
    }

    private void publishMissionStatusChanged(
            Mission mission,
            MissionStatus oldStatus
    ) {
        MissionStatusChangedPayload payload =
                new MissionStatusChangedPayload();

        payload.setMissionId(mission.getMissionId());
        payload.setFarmId(mission.getFarmId());
        payload.setUavId(mission.getUavId());
        payload.setGatewayId(mission.getGatewayId());

        payload.setOldStatus(
                oldStatus == null
                        ? null
                        : vn.farm.contracts.events.missionstatuschanged
                        .MissionStatus.fromValue(oldStatus.name())
        );

        payload.setNewStatus(
                vn.farm.contracts.events.missionstatuschanged
                        .MissionStatus.fromValue(mission.getStatus().name())
        );

        payload.setTotalNodes(mission.getTotalNodes());
        payload.setSuccessNodes(mission.getSuccessNodes());
        payload.setFailedNodes(mission.getFailedNodes());

        payload.setChangedAt(OffsetDateTime.now(ZoneOffset.UTC));

        missionOutboxService.saveEvent(
                FarmEvents.MISSION_STATUS_CHANGED,
                payload
        );
    }

    @Transactional
    public MissionAckResponse ackMission(
            UUID missionId,
            MissionAckRequest request,
            String clientId
    ) {
        MissionExecution execution = missionExecutionRepository
                .findForAck(request.getExecutionId(), missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy MissionExecution"
                ));

        Mission mission = execution.getMission();

        if (mission.getStatus() != MissionStatus.RUNNING) {
            throw BusinessException.invalid(
                    "INVALID_MISSION_STATUS",
                    "Mission không ở trạng thái RUNNING"
            );
        }

        // Kiểm tra Simulator được phân công
        if (clientId == null
                || execution.getAssignedClientId() == null
                || !clientId.equals(execution.getAssignedClientId())) {
            throw BusinessException.invalid(
                    "SIMULATOR_NOT_ASSIGNED",
                    "Simulator không được phân công Mission này"
            );
        }

        // ACK trùng: trả lại kết quả đã xác nhận
        if (execution.getStatus() == MissionExecutionStatus.EXECUTING
                && execution.getAcknowledgedAt() != null) {
            return new MissionAckResponse(
                    missionId,
                    execution.getExecutionId(),
                    execution.getStatus().name(),
                    execution.getAcknowledgedAt()
            );
        }

        if (execution.getStatus() != MissionExecutionStatus.WAITING_ACK) {
            throw BusinessException.invalid(
                    "INVALID_EXECUTION_STATUS",
                    "Execution không ở trạng thái WAITING_ACK"
            );
        }

        Instant now = Instant.now();

        if (execution.getAckDeadline() == null
                || now.isAfter(execution.getAckDeadline())) {
            throw BusinessException.invalid(
                    "ACK_DEADLINE_EXPIRED",
                    "Đã quá thời hạn xác nhận Mission"
            );
        }

        execution.setStatus(MissionExecutionStatus.EXECUTING);
        execution.setAcknowledgedAt(now);
        missionExecutionRepository.save(execution);

        return new MissionAckResponse(
                missionId,
                execution.getExecutionId(),
                execution.getStatus().name(),
                execution.getAcknowledgedAt()
        );
    }

    private MissionResponse buildMissionResponse(Mission mission) {

        MissionResponse response =
                missionMapper.toMissionResponse(mission);

        UUID missionId = mission.getMissionId();

        List<MissionNode> missionNodes =
                missionNodeRepository.findByMission_MissionId(missionId);

        List<Waypoint> waypoints =
                waypointRepository.findByMission_MissionIdOrderBySeqAsc(missionId);

        response.setNodeIds(
                missionNodes.stream()
                        .map(MissionNode::getNodeId)
                        .toList()
        );

        response.setWaypoints(
                waypoints.stream()
                        .map(missionMapper::toWaypointResponse)
                        .toList()
        );

        return response;
    }

    private boolean isSameMissionResults(
            List<MissionNode> missionNodes,
            MissionResultRequest request
    ) {
        if (!Boolean.TRUE.equals(request.getCompleted())) {
            return false;
        }

        if (missionNodes.size() != request.getResults().size()) {
            return false;
        }

        Map<UUID, MissionNode> nodeMap = missionNodes.stream()
                .collect(Collectors.toMap(
                        MissionNode::getNodeId,
                        Function.identity()
                ));

        Set<UUID> receivedNodeIds = new HashSet<>();

        for (MissionNodeResultRequest item : request.getResults()) {

            if (!receivedNodeIds.add(item.getNodeId())) {
                return false;
            }

            MissionNode node = nodeMap.get(item.getNodeId());

            if (node == null) {
                return false;
            }

            if (!Objects.equals(node.getResult(), item.getResult().name())
                    || !Objects.equals(node.getRecordsCount(), item.getRecordsCount())
                    || !Objects.equals(node.getAttempts(), item.getAttempts())
                    || !Objects.equals(node.getCollectedAt(), item.getCollectedAt())
                    || !Objects.equals(node.getErrorCode(), item.getErrorCode())
                    || !Objects.equals(node.getErrorMessage(), item.getErrorMessage())) {
                return false;
            }
        }

        return true;
    }


    @Transactional(readOnly = true)
    public MissionExecutionProgressResponse getExecutionProgress(
            UUID missionId,
            UUID executionId,
            String clientId
    ) {
        // 1. Validate Simulator identity
        if (clientId == null || clientId.isBlank()) {
            throw BusinessException.invalid(
                    "SIMULATOR_CLIENT_ID_REQUIRED",
                    "Không xác định được Simulator"
            );
        }

        // 2. Find Execution
        MissionExecution execution = missionExecutionRepository
                .findByExecutionIdAndMission_MissionId(
                        executionId,
                        missionId
                )
                .orElseThrow(() -> BusinessException.notFound(
                        "EXECUTION_NOT_FOUND",
                        "Không tìm thấy Mission Execution"
                ));

        // 3. Verify Simulator ownership
        if (!clientId.equals(execution.getAssignedClientId())) {
            throw BusinessException.invalid(
                    "SIMULATOR_NOT_ASSIGNED",
                    "Simulator không được phân công Mission này"
            );
        }

        // 4. Return current Execution state
        return new MissionExecutionProgressResponse(
                missionId,
                execution.getExecutionId(),
                execution.getStatus().name(),
                execution.getLastConfirmedSequence(),
                execution.getAcknowledgedAt(),
                execution.getLastHeartbeatAt()
        );
    }

}
