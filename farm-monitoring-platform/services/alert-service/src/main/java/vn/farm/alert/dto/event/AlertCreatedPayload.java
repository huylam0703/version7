package vn.farm.alert.dto.event;

import java.time.Instant;
import java.util.UUID;

public record AlertCreatedPayload(
        UUID alertId,
        String type,
        String severity,
        String message,
        UUID thresholdId,
        UUID readingId,
        UUID nodeId,
        UUID zoneId,
        UUID missionId,
        Double triggerValue,
        Instant triggeredAt
) {}
