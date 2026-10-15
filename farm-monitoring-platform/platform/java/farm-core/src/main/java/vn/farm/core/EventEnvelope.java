package vn.farm.core;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventEnvelope<T>(UUID eventId, String eventType, int version,
                               OffsetDateTime occurredAt, String producer, String traceId, T payload) {}