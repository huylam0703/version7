package vn.farm.mission.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.farm.mission.dto.request.CreateMissionRequest;
import vn.farm.mission.dto.response.MissionDetailResponse;
import vn.farm.mission.dto.response.MissionResponse;
import vn.farm.mission.dto.response.WaypointResponse;
import vn.farm.mission.entity.Mission;
import vn.farm.mission.entity.Waypoint;

@Mapper(componentModel = "spring")
public interface MissionMapper {
    @Mapping(target="missionId",ignore=true)
    @Mapping(target="createdBy",ignore=true)
    @Mapping(target="status",ignore=true)
    @Mapping(target="totalNodes",ignore=true)
    @Mapping(target="successNodes",ignore=true)
    @Mapping(target="failedNodes",ignore=true)
    @Mapping(target="startedAt",ignore=true)
    @Mapping(target="endedAt",ignore=true)
    @Mapping(target="notes",ignore=true)
    Mission toMission(CreateMissionRequest request);

    MissionResponse toMissionResponse(Mission mission);

    @Mapping(target = "waypoints", ignore = true)
    @Mapping(target = "nodeIds", ignore = true)
    MissionDetailResponse toMissionDetailResponse(Mission mission);

    @Mapping(source = "altM", target = "altitudeM")
    WaypointResponse toWaypointResponse(Waypoint waypoint);


}
