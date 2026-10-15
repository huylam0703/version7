package vn.farm.starter.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.farm.core.FarmEvents;

@AutoConfiguration(after = {RabbitAutoConfiguration.class, JacksonAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@ConditionalOnClass(RabbitTemplate.class)
public class FarmEventsAutoConfiguration {

    @Bean
    TopicExchange farmEventsExchange() {
        return new TopicExchange(FarmEvents.EXCHANGE, true, false);
    }

    @Bean
    EventPublisher eventPublisher(RabbitTemplate rabbit, ObjectMapper mapper,
                                  @Value("${spring.application.name}") String service) {
        return new EventPublisher(rabbit, mapper, service);
    }

    @Bean
    EventDeduplicator eventDeduplicator(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        return new EventDeduplicator(jdbc, tm);
    }

    @Bean
    FarmEventListenerRegistrar farmEventListenerRegistrar(ApplicationContext ctx, ConnectionFactory cf,
                                                          AmqpAdmin admin, ObjectMapper mapper, EventDeduplicator dedup,
                                                          @Value("${spring.application.name}") String service) {
        return new FarmEventListenerRegistrar(ctx, cf, admin, mapper, dedup, service);
    }
}