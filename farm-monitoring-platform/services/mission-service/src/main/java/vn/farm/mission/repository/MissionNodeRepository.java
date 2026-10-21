package vn.farm.mission.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.MissionNode;

import java.util.UUID;

@Repository
public interface MissionNodeRepository extends JpaRepository<MissionNode, UUID> {
}
