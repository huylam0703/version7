package vn.farm.mission.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.mission.dto.request.CreateMissionRequest;
import vn.farm.mission.dto.request.WaypointRequest;
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

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MissionService {
    final MissionRepository missions;
    final WaypointRepository waypoints;
    final MissionNodeRepository nodes;
    final MissionStatusLogRepository logs;
    final MissionMapper mapper;
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
        Mission mission = mapper.toMission(request);
        mission.setCreatedBy(currentUser.id());
        mission.setStatus(MissionStatus.DRAFT);
        mission.setTotalNodes(request.getNodeIds().size());
        mission = missions.save(mission);

        List<Waypoint> saveWaypoints = new ArrayList<>();
        for (WaypointRequest w : request.getWaypoints()){
            Waypoint wp = new Waypoint();
            wp.setMission(mission);
            wp.setSeq(w.getSeq().shortValue());
            wp.setLat(w.getLat());
            wp.setLng(w.getLng());
            wp.setAltM(w.getAltitudeM() == null ? BigDecimal.ZERO : w.getAltitudeM());
            saveWaypoints.add(waypoints.save(wp));
        }

        for (UUID nodeId : request.getNodeIds()){
            MissionNode node = new MissionNode();
            node.setMission(mission);
            node.setNodeId(nodeId);
            nodes.save(node);
        }

        MissionStatusLog log = new MissionStatusLog();
        log.setMission(mission);
        log.setChangedBy(mission.getCreatedBy());
        log.setSource("SYSTEM");
        log.setNewStatus("DRAFT");
        log.setCreatedAt(Instant.now());
        logs.save(log);

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
}
