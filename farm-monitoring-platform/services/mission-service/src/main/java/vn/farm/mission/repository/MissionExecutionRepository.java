package vn.farm.mission.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.farm.mission.entity.MissionExecution;
import vn.farm.mission.entity.MissionExecutionStatus;
import vn.farm.mission.entity.MissionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionExecutionRepository
        extends JpaRepository<MissionExecution, UUID> {

    Optional<MissionExecution> findByMission_MissionId(UUID missionId);

    boolean existsByMission_MissionId(UUID missionId);

    List<MissionExecution> findByStatus(
            MissionExecutionStatus status
    );

    Optional<MissionExecution> findByExecutionIdAndMission_MissionId(
            UUID executionId,
            UUID missionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT e
       FROM MissionExecution e
       WHERE e.executionId = :executionId
         AND e.mission.missionId = :missionId
       """)
    Optional<MissionExecution> findForAck(
            @Param("executionId") UUID executionId,
            @Param("missionId") UUID missionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT e
    FROM MissionExecution e
    WHERE e.executionId = :executionId
      AND e.status = :status
    """)
    Optional<MissionExecution> findByIdAndStatusForUpdate(
            @Param("executionId") UUID executionId,
            @Param("status") MissionExecutionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT e
    FROM MissionExecution e
    WHERE e.executionId = :executionId
      AND e.mission.missionId = :missionId
    """)
    Optional<MissionExecution> findForProgress(
            @Param("executionId") UUID executionId,
            @Param("missionId") UUID missionId
    );

    @Query("""
    SELECT e
    FROM MissionExecution e
    JOIN FETCH e.mission m
    WHERE e.assignedClientId = :clientId
      AND e.status = :executionStatus
      AND m.status = :missionStatus
      AND (
          e.lastHeartbeatAt IS NULL
          OR e.lastHeartbeatAt < :heartbeatCutoff
      )
    """)
    List<MissionExecution> findRecoveryCandidates(
            @Param("clientId") String clientId,
            @Param("executionStatus") MissionExecutionStatus executionStatus,
            @Param("missionStatus") MissionStatus missionStatus,
            @Param("heartbeatCutoff") Instant heartbeatCutoff
    );
}