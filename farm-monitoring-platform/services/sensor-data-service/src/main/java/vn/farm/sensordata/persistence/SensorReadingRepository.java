package vn.farm.sensordata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.farm.sensordata.domain.SensorReading;

public interface SensorReadingRepository
        extends JpaRepository<SensorReading, Long> {

    boolean existsByNodeIdAndSensorTypeAndMeasuredAt(
            String nodeId,
            String sensorType,
            java.time.LocalDateTime measuredAt
    );
}
