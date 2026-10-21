package vn.farm.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.Waypoint;

import java.util.List;
import java.util.UUID;

@Repository
public interface WaypointRepository extends JpaRepository<Waypoint, UUID> {
    List<Waypoint> findByMission_MissionIdOrderBySeqAsc(UUID missionId);
}
