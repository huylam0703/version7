package vn.farm.alert.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.alert.config.RabbitMQConfig;
import vn.farm.alert.outbox.AlertOutboxRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final AlertOutboxRepository repository;

    @Transactional
    public boolean publish(UUID eventId) {
        var event = repository.findForPublishing(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null || event.getNextRetryAt().isAfter(Instant.now())) {
            return true;
        }

        String messageId = eventId.toString();
        var message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setMessageId(messageId)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        var correlation = new CorrelationData(messageId);

        try {
            rabbitTemplate.send(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ALERT_CREATED_ROUTING_KEY,
                    message, correlation);
            var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ did not confirm a routed alert.created event: "
                        + confirm.getReason());
            }
            event.markPublished();
            return true;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            event.scheduleRetry();
            log.warn("Will retry alert.created event {} at {}", eventId, event.getNextRetryAt(), exception);
            return false;
        }
    }
}
