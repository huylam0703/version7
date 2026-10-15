package vn.farm.starter.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.farm.core.EventEnvelope;
import vn.farm.core.FarmEvents;

public class EventPublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final String producer;

    public EventPublisher(RabbitTemplate rabbit, ObjectMapper mapper, String producer) {
        this.rabbit = rabbit; this.mapper = mapper; this.producer = producer;
    }

    public void publish(String eventType, Object payload) {
        EventEnvelope<Object> env = new EventEnvelope<>(UUID.randomUUID(), eventType, 1,
                OffsetDateTime.now(ZoneOffset.UTC), producer, MDC.get("traceId"), payload);
        // Đang trong transaction thì chỉ gửi sau khi commit thành công
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send(env); }
            });
        } else {
            send(env);
        }
    }

    private void send(EventEnvelope<Object> env) {
        try {
            Message msg = MessageBuilder.withBody(mapper.writeValueAsBytes(env))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setMessageId(env.eventId().toString())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbit.send(FarmEvents.EXCHANGE, env.eventType(), msg);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được event " + env.eventType(), e);
        }
    }
}