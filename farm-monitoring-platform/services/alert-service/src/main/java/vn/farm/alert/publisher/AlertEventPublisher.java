package vn.farm.alert.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import vn.farm.alert.config.RabbitMQConfig;
import vn.farm.alert.dto.event.AlertCreatedPayload;
import vn.farm.alert.dto.event.EventEnvelope;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishAlertCreated(AlertCreatedPayload payload) {

        EventEnvelope<AlertCreatedPayload> event =
                new EventEnvelope<>(
                        UUID.randomUUID(),
                        "alert.created",
                        1,
                        Instant.now(),
                        "alert-service",
                        "",
                        payload
                );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    RabbitMQConfig.ALERT_CREATED_ROUTING_KEY,
                    event
            );

            log.info(
                    "Submitted alert.created event: eventId={}, alertId={}",
                    event.eventId(),
                    payload.alertId()
            );

        } catch (Exception e) {
            log.error(
                    "Failed to submit alert.created event: eventId={}, alertId={}",
                    event.eventId(),
                    payload.alertId(),
                    e
            );

            throw new IllegalStateException(
                    "Failed to publish alert.created event",
                    e
            );
        }
    }
}
