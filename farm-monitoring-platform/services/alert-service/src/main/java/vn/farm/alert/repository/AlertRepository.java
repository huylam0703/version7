package vn.farm.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertStatus;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByNodeId(UUID nodeId);

    List<Alert> findByStatus(AlertStatus status);

    List<Alert> findByNodeIdAndStatus(
            UUID nodeId,
            AlertStatus status
    );

    boolean existsByMissionIdAndAlertType(UUID missionId, String alertType);

    boolean existsByReadingId(UUID readingId);

    List<Alert> findTop50ByNotificationPendingTrueOrderByCreatedAtAsc();

    @Transactional
    @Modifying
    @Query("update Alert a set a.notificationPending = false where a.id = :id")
    void markNotificationPublished(UUID id);
}
