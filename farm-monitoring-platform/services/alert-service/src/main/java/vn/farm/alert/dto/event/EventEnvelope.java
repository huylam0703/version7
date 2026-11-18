package vn.farm.alert.dto.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        String traceId,
        T payload
) {}
