package vn.farm.mission.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.Mission;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID>, JpaSpecificationExecutor<Mission> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT m
    FROM Mission m
    WHERE m.missionId = :missionId
    """)
    Optional<Mission> findForResults(
            @Param("missionId") UUID missionId
    );
}
