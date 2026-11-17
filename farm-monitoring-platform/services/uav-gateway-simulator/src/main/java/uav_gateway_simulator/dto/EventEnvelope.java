package uav_gateway_simulator.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Integer version,
        OffsetDateTime occurredAt,
        String producer,
        String traceId,
        T payload
) {}
