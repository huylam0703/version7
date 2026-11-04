package vn.farm.sensordata.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.farm.sensordata.api.dto.SensorReadingRequest;
import vn.farm.sensordata.domain.SensorReading;
import vn.farm.sensordata.persistence.SensorReadingRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class SensorReadingService {

    private final SensorReadingRepository repository;

    public SensorReadingService(SensorReadingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> saveReading(SensorReadingRequest request) {

        // 1. Kiểm tra dữ liệu trùng
        boolean exists = repository.existsByNodeIdAndSensorTypeAndMeasuredAt(
                request.nodeId(),
                request.sensorType(),
                request.measuredAt()
        );

        if (exists) {
            return Map.of(
                    "status", "DUPLICATE",
                    "message", "Sensor reading already exists"
            );
        }

        // 2. Kiểm tra giá trị cảm biến
        String validationStatus = validateSensorValue(
                request.sensorType(),
                request.sensorValue()
        );

        // 3. Chuyển DTO thành Entity
        SensorReading reading = new SensorReading();

        reading.setNodeId(request.nodeId());
        reading.setSensorType(request.sensorType());
        reading.setSensorValue(request.sensorValue());
        reading.setMeasuredAt(request.measuredAt());
        reading.setCollectedAt(request.collectedAt());
        reading.setLatitude(request.latitude());
        reading.setLongitude(request.longitude());
        reading.setValidationStatus(validationStatus);

        // 4. Lưu vào SQL Server
        SensorReading saved = repository.save(reading);

        // 5. Trả kết quả
        return Map.of(
                "status", validationStatus,
                "message", "Sensor reading saved",
                "id", saved.getId()
        );
    }

    private String validateSensorValue(String sensorType, Double value) {

        if (value == null || !Double.isFinite(value)) {
            return "INVALID";
        }

        if (sensorType == null) {
            return "INVALID";
        }

        switch (sensorType.trim().toUpperCase()) {
            case "TEMPERATURE":
                // Giới hạn thử nghiệm, sẽ cấu hình theo loại cảm biến
                return value >= -40 && value <= 85
                        ? "VALID" : "INVALID";

            case "HUMIDITY":
            case "SOIL_MOISTURE":
                return value >= 0 && value <= 100
                        ? "VALID" : "INVALID";

            case "PH":
                return value >= 0 && value <= 14
                        ? "VALID" : "INVALID";

            default:
                return "INVALID";
        }
    }
}
