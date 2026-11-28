package vn.farm.alert.dto.event;

import java.time.Instant;
import java.util.UUID;

public record ReadingsSyncedPayload(
        UUID readingId,
        UUID nodeId,
        UUID zoneId,
        UUID typeId,
        UUID channelId,
        UUID missionId,
        Double value,
        Instant measuredAt,
        UUID farmId,
        String sensorCode,
        String unit
) {
}
