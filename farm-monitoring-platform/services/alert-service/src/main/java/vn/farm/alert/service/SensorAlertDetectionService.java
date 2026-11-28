package vn.farm.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.farm.alert.config.SensorAlertProperties;
import vn.farm.alert.dto.event.ReadingsSyncedPayload;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.SensorThreshold;
import vn.farm.alert.repository.SensorThresholdRepository;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorAlertDetectionService {

    private final SensorAlertProperties properties;
    private final SensorThresholdRepository thresholds;

    public DetectionResult detect(ReadingsSyncedPayload reading) {

        // 1. Validate dữ liệu đầu vào
        if (reading == null
                || reading.readingId() == null
                || reading.nodeId() == null
                || (reading.typeId() == null && reading.sensorCode() == null)
                || reading.value() == null
                || !Double.isFinite(reading.value())
                || reading.measuredAt() == null) {

            log.warn("Invalid sensor reading received");
            return DetectionResult.invalid();
        }

        // 2. Ánh xạ typeId sang mã loại cảm biến
        Map<String, String> typeMappings =
                properties.getTypeMappings();

        String sensorCode = reading.sensorCode() != null ? reading.sensorCode()
                : typeMappings.get(reading.typeId().toString());

        if (sensorCode == null || sensorCode.isBlank()) {
            log.warn(
                    "No sensor type mapping found for typeId={}",
                    reading.typeId()
            );
            return DetectionResult.unconfigured();
        }

        var configured = thresholds.findById(SensorThreshold.key(reading.farmId(), sensorCode));
        if (configured.isEmpty() && reading.farmId() != null) {
            configured = thresholds.findById(SensorThreshold.key(null, sensorCode));
        }
        if (configured.isPresent()) {
            SensorThreshold rule = configured.get();
            return evaluate(reading, sensorCode, rule.getMinValue(), rule.getMaxValue(), rule.getSeverity());
        }

        // 3. Lấy ngưỡng cảnh báo
        SensorAlertProperties.Threshold threshold =
                properties.getThresholds().get(sensorCode);

        if (threshold == null
                || threshold.getMin() == null
                || threshold.getMax() == null
                || !Double.isFinite(threshold.getMin())
                || !Double.isFinite(threshold.getMax())
                || threshold.getMin() > threshold.getMax()) {

            log.warn(
                    "Invalid or missing threshold for sensorCode={}",
                    sensorCode
            );
            return DetectionResult.unconfigured();
        }

        return evaluate(reading, sensorCode, threshold.getMin(), threshold.getMax(), null);
    }

    private DetectionResult evaluate(ReadingsSyncedPayload reading, String sensorCode,
                                     double min, double max, AlertSeverity configuredSeverity) {
        double value = reading.value();
        // 4. Dữ liệu nằm trong ngưỡng bình thường
        if (value >= min && value <= max) {
            log.info(
                    "Normal sensor reading: type={}, value={}",
                    sensorCode,
                    value
            );
            return DetectionResult.normal();
        }

        // 5. Xác định mức độ cảnh báo
        double range = max - min;

        double exceedAmount = value < min
                ? min - value
                : value - max;

        AlertSeverity severity;

        if (configuredSeverity != null) {
            severity = configuredSeverity;
        } else if (range == 0 || exceedAmount > range * 0.5) {
            severity = AlertSeverity.CRITICAL;
        } else if (exceedAmount > range * 0.2) {
            severity = AlertSeverity.HIGH;
        } else {
            severity = AlertSeverity.MEDIUM;
        }

        // 6. Tạo thông tin cảnh báo
        String direction = value < min
                ? "below minimum"
                : "above maximum";

        String message = String.format(
                "Sensor %s value %.2f %s is %s threshold [%.2f, %.2f] at %s",
                sensorCode,
                value,
                reading.unit() == null ? "" : reading.unit(),
                direction,
                min,
                max,
                reading.measuredAt()
        );

        log.warn(
                "Sensor threshold exceeded: nodeId={}, type={}, value={}, severity={}",
                reading.nodeId(),
                sensorCode,
                value,
                severity
        );

        return DetectionResult.alert(
                sensorCode,
                severity,
                message
        );
    }

    public record DetectionResult(
            Status status,
            String sensorCode,
            AlertSeverity severity,
            String message
    ) {

        public enum Status {
            INVALID,
            UNCONFIGURED,
            NORMAL,
            ALERT
        }

        public static DetectionResult invalid() {
            return new DetectionResult(
                    Status.INVALID, null, null, null
            );
        }

        public static DetectionResult unconfigured() {
            return new DetectionResult(
                    Status.UNCONFIGURED, null, null, null
            );
        }

        public static DetectionResult normal() {
            return new DetectionResult(
                    Status.NORMAL, null, null, null
            );
        }

        public static DetectionResult alert(
                String sensorCode,
                AlertSeverity severity,
                String message
        ) {
            return new DetectionResult(
                    Status.ALERT,
                    sensorCode,
                    severity,
                    message
            );
        }
    }
}
