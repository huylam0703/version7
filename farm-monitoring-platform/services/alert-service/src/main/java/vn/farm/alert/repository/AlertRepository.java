package vn.farm.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.farm.alert.entity.Alert;

import java.util.UUID;

public interface AlertRepository
        extends JpaRepository<Alert, UUID> {
}