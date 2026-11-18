package vn.farm.alert.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitConnectionCheck implements ApplicationRunner {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void run(ApplicationArguments args) {

        try {
            rabbitTemplate.execute(channel -> {
                log.info("RabbitMQ connected successfully!");
                return null;
            });

        } catch (Exception e) {
            log.error("RabbitMQ connection failed", e);
        }
    }
}
