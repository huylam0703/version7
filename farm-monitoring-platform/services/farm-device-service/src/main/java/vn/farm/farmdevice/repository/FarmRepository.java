package vn.farm.farmdevice.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.farm.farmdevice.entity.Farm;

public interface FarmRepository extends JpaRepository<Farm, UUID> {
    boolean existsByNameIgnoreCase(String name);
}