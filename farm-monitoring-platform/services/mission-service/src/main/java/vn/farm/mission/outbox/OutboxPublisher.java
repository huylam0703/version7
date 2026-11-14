package vn.farm.mission.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import vn.farm.core.FarmEvents;
import vn.farm.mission.entity.OutboxEvent;
import vn.farm.mission.entity.OutboxStatus;
import vn.farm.mission.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventRepository repository;
    private final OutboxStateService stateService;

    public void publish(UUID eventId) {

        OutboxEvent event = repository.findById(eventId)
                .orElseThrow();

        if (event.getStatus() != OutboxStatus.PROCESSING) {
            return;
        }

        UUID lockToken = event.getLockToken();

        if (lockToken == null) {
            return;
        }

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload()
                            .getBytes(StandardCharsets.UTF_8))
                    .setContentType(
                            MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setMessageId(eventId.toString())
                    .setDeliveryMode(
                            MessageDeliveryMode.PERSISTENT)
                    .build();

            CorrelationData correlationData =
                    new CorrelationData(eventId.toString());

            rabbitTemplate.send(
                    FarmEvents.EXCHANGE,
                    event.getEventType(),
                    message,
                    correlationData
            );

            CorrelationData.Confirm confirm =
                    correlationData.getFuture()
                            .get(10, TimeUnit.SECONDS);

            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ NACK: " + confirm.getReason()
                );
            }

            if (correlationData.getReturned() != null) {
                throw new IllegalStateException(
                        "Message cannot be routed to queue"
                );
            }

            stateService.markPublished(eventId, lockToken);

            log.info("Outbox event published: {}", eventId);

        } catch (Exception e) {

            log.error(
                    "Failed to publish outbox event {}",
                    eventId,
                    e
            );

            stateService.markFailedOrRetry(eventId, lockToken);
        }
    }
}