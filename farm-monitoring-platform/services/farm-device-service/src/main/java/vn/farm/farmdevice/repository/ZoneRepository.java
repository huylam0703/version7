package vn.farm.farmdevice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.farm.farmdevice.entity.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID>, JpaSpecificationExecutor<Zone> {
    boolean existsByFarmIdAndCodeIgnoreCase(UUID farmId, String code);

    boolean existsByFarmIdAndCodeIgnoreCaseAndIdNot(UUID farmId, String code, UUID id);
}
