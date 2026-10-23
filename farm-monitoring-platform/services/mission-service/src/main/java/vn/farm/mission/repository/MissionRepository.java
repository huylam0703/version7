package vn.farm.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.Mission;

import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID>, JpaSpecificationExecutor<Mission> {
}
