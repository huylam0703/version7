package vn.farm.alert.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.alert.config.RabbitMQConfig;
import vn.farm.alert.dto.event.AlertCreatedPayload;
import vn.farm.alert.dto.event.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertOutboxService {
    private final AlertOutboxRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveAlertCreated(AlertCreatedPayload payload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        var envelope = new EventEnvelope<>(eventId, RabbitMQConfig.ALERT_CREATED_ROUTING_KEY,
                1, now, "alert-service", "", payload);

        try {
            repository.save(new AlertOutboxEvent(eventId, objectMapper.writeValueAsString(envelope), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize alert.created event " + eventId, exception);
        }
    }
}
