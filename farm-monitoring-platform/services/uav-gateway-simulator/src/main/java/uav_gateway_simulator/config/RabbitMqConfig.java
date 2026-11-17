package uav_gateway_simulator.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "farm.events";
    public static final String QUEUE = "simulator.mission.ready";
    public static final String ROUTING_KEY = "mission.ready";

    @Bean
    public TopicExchange farmExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue missionReadyQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding missionReadyBinding(
            Queue missionReadyQueue,
            TopicExchange farmExchange
    ) {
        return BindingBuilder
                .bind(missionReadyQueue)
                .to(farmExchange)
                .with(ROUTING_KEY);
    }
}
