package vn.farm.farmdevice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.farm.farmdevice.entity.Uav;
import vn.farm.farmdevice.entity.UavStatus;

public interface UavRepository extends JpaRepository<Uav, UUID>, JpaSpecificationExecutor<Uav> {
    boolean existsBySerialNoIgnoreCase(String serialNo);

    boolean existsByFarmIdAndStatus(UUID farmId, UavStatus status);
}
