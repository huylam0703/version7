package vn.farm.sensordata.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "sensor_readings",
    uniqueConstraints = @UniqueConstraint(
        name = SensorReading.UNIQUE_CONSTRAINT,
        columnNames = {"node_id", "sensor_type", "measured_at"}
    )
)
public class SensorReading {

    public static final String UNIQUE_CONSTRAINT = "uq_sensor_reading";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "sensor_type", nullable = false, length = 50)
    private String sensorType;

    @Column(name = "sensor_value", nullable = false)
    private Double sensorValue;

    @Nationalized
    @Column(name = "unit", length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_kind", length = 20)
    private ReadingValueKind valueKind;

    @Column(name = "adc_resolution_bits")
    private Integer adcResolutionBits;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "validation_status", nullable = false, length = 20)
    private String validationStatus = "VALID";

    @Embedded
    private ReadingContext context;

    public SensorReading() {
    }

    public Long getId() {
        return id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public Double getSensorValue() {
        return sensorValue;
    }

    public void setSensorValue(Double sensorValue) {
        this.sensorValue = sensorValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public ReadingValueKind getValueKind() {
        return valueKind;
    }

    public void setValueKind(ReadingValueKind valueKind) {
        this.valueKind = valueKind;
    }

    public Integer getAdcResolutionBits() {
        return adcResolutionBits;
    }

    public void setAdcResolutionBits(Integer adcResolutionBits) {
        this.adcResolutionBits = adcResolutionBits;
    }

    public LocalDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(LocalDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public void setContext(ReadingContext context) {
        this.context = context;
    }
}
