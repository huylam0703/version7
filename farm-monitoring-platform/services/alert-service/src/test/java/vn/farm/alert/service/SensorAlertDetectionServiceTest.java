package vn.farm.alert.service;

import org.junit.jupiter.api.Test;
import vn.farm.alert.config.SensorAlertProperties;
import vn.farm.alert.dto.event.ReadingsSyncedPayload;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.SensorThreshold;
import vn.farm.alert.repository.SensorThresholdRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static vn.farm.alert.service.SensorAlertDetectionService.DetectionResult.Status.ALERT;
import static vn.farm.alert.service.SensorAlertDetectionService.DetectionResult.Status.NORMAL;

class SensorAlertDetectionServiceTest {
    private final UUID farmId = UUID.randomUUID();
    private final SensorThresholdRepository thresholds = mock(SensorThresholdRepository.class);
    private final SensorAlertDetectionService detection =
            new SensorAlertDetectionService(new SensorAlertProperties(), thresholds);

    @Test
    void farmThresholdOverridesGlobalAndIncludesBothBoundaries() {
        configure(null, 10, 40, AlertSeverity.MEDIUM);
        configure(farmId, 18, 35, AlertSeverity.HIGH);

        assertThat(detection.detect(reading(farmId, 18)).status()).isEqualTo(NORMAL);
        assertThat(detection.detect(reading(farmId, 35)).status()).isEqualTo(NORMAL);
        assertThat(detection.detect(reading(farmId, 39)).severity()).isEqualTo(AlertSeverity.HIGH);
        assertThat(detection.detect(reading(farmId, 15)).message()).contains("below minimum");
        assertThat(detection.detect(reading(UUID.randomUUID(), 39)).status()).isEqualTo(NORMAL);
        assertThat(detection.detect(reading(null, 41)).status()).isEqualTo(ALERT);
    }

    private void configure(UUID farm, double min, double max, AlertSeverity severity) {
        var threshold = new SensorThreshold(farm, "AIR_TEMP", min, max, severity);
        when(thresholds.findById(threshold.getId())).thenReturn(Optional.of(threshold));
    }

    private ReadingsSyncedPayload reading(UUID farm, double value) {
        return new ReadingsSyncedPayload(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
                value, Instant.parse("2026-11-28T00:00:00Z"), farm, "AIR_TEMP", "°C");
    }
}
