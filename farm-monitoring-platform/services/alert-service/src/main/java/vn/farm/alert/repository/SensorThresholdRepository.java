package vn.farm.alert.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.farm.alert.entity.SensorThreshold;

public interface SensorThresholdRepository extends JpaRepository<SensorThreshold, String> {}
