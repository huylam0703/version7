package vn.farm.sensordata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.sensordata.domain.ReadingValueKind;
import vn.farm.sensordata.domain.SensorReading;
import vn.farm.sensordata.persistence.SensorReadingRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "farm.reading-alerts.enabled", havingValue = "true")
public class ReadingAlertOutbox {
    private static final Logger log = LoggerFactory.getLogger(ReadingAlertOutbox.class);
    private static final String EXCHANGE = "farm.events";
    private static final String ROUTING_KEY = "readings.synced";
    private final SensorReadingRepository readings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;

    public ReadingAlertOutbox(SensorReadingRepository readings, JdbcTemplate jdbc,
                              ObjectMapper mapper, RabbitTemplate rabbit) {
        this.readings = readings;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rabbit = rabbit;
    }

    @Bean
    TopicExchange readingAlertExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Transactional
    public SensorReading save(SensorReading reading, boolean valid) {
        SensorReading saved = readings.saveAndFlush(reading);
        if (!valid || reading.getValueKind() == ReadingValueKind.ADC_RAW) {
            return saved;
        }

        UUID nodeId;
        try {
            nodeId = UUID.fromString(reading.getNodeId());
        } catch (IllegalArgumentException ignored) {
            // Legacy readings can have a node label instead of a registered node UUID.
            return saved;
        }

        var context = reading.getContext();
        var payload = new Payload(
                new UUID(0, saved.getId()), nodeId, context == null ? null : context.zoneId(),
                context == null ? null : context.farmId(), context == null ? null : context.missionId(),
                reading.getSensorType(), reading.getSensorValue(), reading.getUnit(),
                reading.getMeasuredAt().toInstant(ZoneOffset.UTC));
        var event = new Envelope(UUID.randomUUID(), ROUTING_KEY, 1, Instant.now(), "sensor-data-service", payload);
        try {
            jdbc.update("INSERT INTO dbo.reading_alert_outbox (reading_id, event_json) VALUES (?, ?)",
                    saved.getId(), mapper.writeValueAsString(event));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize reading alert event", error);
        }
        return saved;
    }

    @Scheduled(fixedDelayString = "${farm.reading-alerts.poll-ms:1000}")
    public void publishPending() {
        var pending = jdbc.query("SELECT TOP (50) reading_id, event_json FROM dbo.reading_alert_outbox ORDER BY reading_id",
                (row, index) -> new Pending(row.getLong(1), row.getString(2)));
        for (Pending item : pending) {
            try {
                var confirmation = new CorrelationData();
                rabbit.convertAndSend(EXCHANGE, ROUTING_KEY, item.json(), message -> {
                    message.getMessageProperties().setContentType("application/json");
                    message.getMessageProperties().setContentEncoding("UTF-8");
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                }, confirmation);
                if (!confirmation.getFuture().get(5, TimeUnit.SECONDS).isAck()
                        || confirmation.getReturned() != null) {
                    log.warn("Reading {} event was not routed. It will be retried.", item.id());
                    return;
                }
                jdbc.update("DELETE FROM dbo.reading_alert_outbox WHERE reading_id = ?", item.id());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                log.warn("Reading {} event publish failed. It will be retried.", item.id(), error);
                return;
            }
        }
    }

    private record Pending(long id, String json) {}
    private record Envelope(UUID eventId, String eventType, int version, Instant occurredAt, String producer,
                            Payload payload) {}
    private record Payload(UUID readingId, UUID nodeId, UUID zoneId, UUID farmId, UUID missionId,
                           String sensorCode, double value, String unit, Instant measuredAt) {}
}
