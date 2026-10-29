package vn.farm.farmdevice.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.SensorNode;

public interface SensorNodeRepository extends JpaRepository<SensorNode, UUID>, JpaSpecificationExecutor<SensorNode> {
    boolean existsByDeviceUidIgnoreCase(String deviceUid);

    Optional<SensorNode> findByDeviceUidIgnoreCase(String deviceUid);

    boolean existsByZoneIdAndStatusIn(UUID zoneId, Collection<NodeStatus> statuses);
}
