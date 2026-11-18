package vn.farm.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}