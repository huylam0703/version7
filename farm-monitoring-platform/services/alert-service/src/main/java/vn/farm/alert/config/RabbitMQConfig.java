package vn.farm.alert.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Task 7: Alert Event Publishing
    public static final String EXCHANGE = "farm.events";
    public static final String ALERT_CREATED_ROUTING_KEY = "alert.created";

    // Task 8: Sensor Data Integration
    public static final String READINGS_SYNCED_QUEUE =
            "alert.readings.synced";

    public static final String READINGS_SYNCED_ROUTING_KEY =
            "readings.synced";

    // JSON Message Converter
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate - Task 7
    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jsonMessageConverter) {

        RabbitTemplate rabbitTemplate =
                new RabbitTemplate(connectionFactory);

        rabbitTemplate.setMessageConverter(jsonMessageConverter);

        return rabbitTemplate;
    }

    // Queue nhận dữ liệu cảm biến - Task 8
    @Bean
    public Queue readingsSyncedQueue() {
        return QueueBuilder
                .durable(READINGS_SYNCED_QUEUE)
                .build();
    }

    // Binding queue với exchange farm.events
    @Bean
    public Binding readingsSyncedBinding(
            Queue readingsSyncedQueue) {

        return BindingBuilder
                .bind(readingsSyncedQueue)
                .to(new TopicExchange(EXCHANGE))
                .with(READINGS_SYNCED_ROUTING_KEY);
    }
}
