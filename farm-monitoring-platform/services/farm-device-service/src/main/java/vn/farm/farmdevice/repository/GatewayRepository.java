package vn.farm.farmdevice.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.farm.farmdevice.entity.Gateway;

public interface GatewayRepository extends JpaRepository<Gateway, UUID>, JpaSpecificationExecutor<Gateway> {
    boolean existsByGatewayUidIgnoreCase(String gatewayUid);

    Optional<Gateway> findByGatewayUidIgnoreCase(String gatewayUid);
}
