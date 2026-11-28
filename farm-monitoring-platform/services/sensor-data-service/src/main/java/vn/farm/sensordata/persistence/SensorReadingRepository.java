package vn.farm.sensordata.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.farm.sensordata.domain.SensorReading;

import java.time.LocalDateTime;
import java.util.Collection;

public interface SensorReadingRepository
        extends JpaRepository<SensorReading, Long> {

    @Query("""
            select case when count(reading) > 0 then true else false end
            from SensorReading reading
            where reading.nodeId = :nodeId
                and reading.measuredAt = :measuredAt
                and upper(trim(reading.sensorType)) in :sensorTypes
            """)
    boolean existsReading(
            @Param("nodeId") String nodeId,
            @Param("sensorTypes") Collection<String> sensorTypes,
            @Param("measuredAt") LocalDateTime measuredAt
    );
}
