package vn.farm.alert.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "sensor_thresholds")
@Getter
@NoArgsConstructor
public class SensorThreshold {
    @Id
    @Column(length = 64)
    private String id;
    private UUID farmId;
    @Column(nullable = false, length = 20)
    private String sensorCode;
    private double minValue;
    private double maxValue;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    public SensorThreshold(UUID farmId, String sensorCode, double minValue, double maxValue, AlertSeverity severity) {
        this.id = key(farmId, sensorCode);
        this.farmId = farmId;
        this.sensorCode = sensorCode;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.severity = severity;
    }

    public static String key(UUID farmId, String sensorCode) {
        return (farmId == null ? "global" : farmId.toString()) + ":" + sensorCode;
    }
}
