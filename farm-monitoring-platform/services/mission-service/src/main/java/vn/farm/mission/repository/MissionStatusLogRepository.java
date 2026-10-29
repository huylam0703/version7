package vn.farm.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.MissionStatusLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionStatusLogRepository extends JpaRepository<MissionStatusLog, UUID> {

    List<MissionStatusLog> findByMission_MissionIdOrderByCreatedAtAsc(UUID missionId);
}
