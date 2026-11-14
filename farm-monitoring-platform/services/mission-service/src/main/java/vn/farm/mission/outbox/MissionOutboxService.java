package vn.farm.mission.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.farm.core.EventEnvelope;
import vn.farm.mission.entity.OutboxEvent;
import vn.farm.mission.entity.OutboxStatus;
import vn.farm.mission.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MissionOutboxService {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void saveEvent(String eventType, Object payload) {
        UUID eventId = UUID.randomUUID();

        EventEnvelope<Object> envelope = new EventEnvelope<>(
                eventId,
                eventType,
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                "mission-service",
                null,
                payload
        );

        try {
            String json = objectMapper.writeValueAsString(envelope);

            OutboxEvent event = OutboxEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .payload(json)
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .createdAt(Instant.now())
                    .nextRetryAt(Instant.now())
                    .build();

            outboxRepository.save(event);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialize outbox event: " + eventType,
                    e
            );
        }
    }
}