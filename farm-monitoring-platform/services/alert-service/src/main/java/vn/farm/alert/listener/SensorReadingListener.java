package vn.farm.alert.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import vn.farm.core.BusinessException;

import vn.farm.alert.config.RabbitMQConfig;
import vn.farm.alert.dto.event.EventEnvelope;
import vn.farm.alert.dto.event.ReadingsSyncedPayload;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.dto.response.AlertResponse;
import vn.farm.alert.service.AlertService;
import vn.farm.alert.service.SensorAlertDetectionService;
import vn.farm.alert.service.SensorAlertDetectionService.DetectionResult;


@Slf4j
@Component
@RequiredArgsConstructor
public class SensorReadingListener {

    private final SensorAlertDetectionService detectionService;
    private final AlertService alertService;

    @RabbitListener(queues = RabbitMQConfig.READINGS_SYNCED_QUEUE)
    public void handleReadingsSynced(
            EventEnvelope<ReadingsSyncedPayload> event
    ) {
        if (event == null || event.version() != 1 || event.payload() == null) {
            log.warn("Received unsupported readings.synced event or empty payload");
            return;
        }

        ReadingsSyncedPayload reading = event.payload();

        log.info(
                "Received readings.synced: readingId={}, nodeId={}, typeId={}, value={}",
                reading.readingId(),
                reading.nodeId(),
                reading.typeId(),
                reading.value()
        );

        DetectionResult result = detectionService.detect(reading);

        switch (result.status()) {

            case NORMAL -> log.info(
                    "Sensor reading is normal: readingId={}",
                    reading.readingId()
            );

            case ALERT -> {
                log.warn(
                        "Sensor alert detected: readingId={}, type={}, severity={}",
                        reading.readingId(),
                        result.sensorCode(),
                        result.severity()
                );

                CreateAlertRequest request = new CreateAlertRequest();
                request.setNodeId(reading.nodeId());
                request.setAlertType("OUT_OF_RANGE");
                request.setZoneId(reading.zoneId());
                request.setMissionId(reading.missionId());
                request.setTriggerValue(reading.value());
                request.setTriggeredAt(reading.measuredAt());
                request.setSeverity(result.severity());
                request.setMessage(result.message());
                // Task 8: Gắn ID bản ghi cảm biến để chống trùng
                request.setReadingId(reading.readingId());

                try {
                    AlertResponse createdAlert = alertService.createAlert(request);

                    log.info(
                            "Automatically created Alert from sensor reading: readingId={}, alertId={}",
                            reading.readingId(),
                            createdAlert.getId()
                    );

                } catch (BusinessException e) {

                    if ("DUPLICATE_SENSOR_ALERT".equals(e.getCode())) {
                        log.info(
                                "Duplicate sensor reading ignored: readingId={}",
                                reading.readingId()
                        );
                    } else {
                        throw e;
                    }
                }
            }

            case UNCONFIGURED -> log.warn(
                    "Sensor type or threshold not configured: typeId={}",
                    reading.typeId()
            );

            case INVALID -> log.warn(
                    "Invalid sensor reading: readingId={}",
                    reading.readingId()
            );
        }
    }
}
