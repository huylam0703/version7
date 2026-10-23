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
import vn.farm.mission.dto.request.*;
import vn.farm.mission.dto.response.MissionDetailResponse;
import vn.farm.mission.dto.response.MissionResponse;
import vn.farm.mission.dto.response.WaypointResponse;
import vn.farm.mission.entity.*;
import vn.farm.mission.mapper.MissionMapper;
import vn.farm.mission.repository.MissionNodeRepository;
import vn.farm.mission.repository.MissionRepository;
import vn.farm.mission.repository.MissionStatusLogRepository;
import vn.farm.mission.repository.WaypointRepository;
import vn.farm.mission.validation.MissionReferenceValidator;
import vn.farm.starter.security.CurrentUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        MissionStatusLog log = new MissionStatusLog();
        log.setMission(mission);
        log.setChangedBy(mission.getCreatedBy());
        log.setSource("SYSTEM");
        log.setNewStatus("DRAFT");
        log.setCreatedAt(Instant.now());
        missionStatusLogRepository.save(log);

        List<WaypointResponse> wpResponse = saveWaypoints.stream()
                .map(waypoint -> new WaypointResponse(waypoint.getWaypointId(),
                        waypoint.getSeq(),
                        waypoint.getLat(),
                        waypoint.getLng(),
                        waypoint.getAltM(),
                        waypoint.getStatus()
                        ))
                .toList();

        return new MissionResponse(mission.getMissionId(),
                mission.getFarmId(),
                mission.getUavId(),
                mission.getGatewayId(),
                mission.getCreatedBy(),
                mission.getName(),
                mission.getStatus().name(),
                mission.getPlannedAt(),
                mission.getTotalNodes(),
                List.copyOf(request.getNodeIds()), wpResponse);
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

        mission.setStatus(MissionStatus.RUNNING);
        mission.setStartedAt(Instant.now());

        Mission saved = missionRepository.save(mission);

        // Cần lưu MissionStatusLog tại đây.

        return missionMapper.toMissionResponse(saved);
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

        mission.setStatus(MissionStatus.CANCELLED);
        mission.setEndedAt(Instant.now());

        Mission saved = missionRepository.save(mission);

        // Cần lưu MissionStatusLog tại đây.

        return missionMapper.toMissionResponse(saved);
    }

    @Transactional
    public MissionResponse receiveMissionResults(
            UUID missionId,
            MissionResultRequest request
    ) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> BusinessException.notFound(
                        "MISSION_NOT_FOUND",
                        "Mission không tồn tại"
                ));

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

            MissionNode node = nodeMap.get(item.getNodeId());

            if (node == null) {
                throw BusinessException.invalid(
                        "INVALID_NODE",
                        "Node không thuộc Mission"
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

        if (failedCount == 0) {
            mission.setStatus(MissionStatus.COMPLETED);
        } else if (successCount == 0) {
            mission.setStatus(MissionStatus.FAILED);
        } else {
            mission.setStatus(MissionStatus.PARTIAL);
        }

        mission.setEndedAt(Instant.now());

        Mission saved = missionRepository.save(mission);

        // Cần lưu MissionStatusLog khi trạng thái thay đổi.

        return missionMapper.toMissionResponse(saved);
    }
}
