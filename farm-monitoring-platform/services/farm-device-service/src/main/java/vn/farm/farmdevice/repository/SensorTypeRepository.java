package vn.farm.farmdevice.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.farm.farmdevice.entity.SensorType;

public interface SensorTypeRepository extends JpaRepository<SensorType, UUID> {
    Optional<SensorType> findByCode(String code);
}
