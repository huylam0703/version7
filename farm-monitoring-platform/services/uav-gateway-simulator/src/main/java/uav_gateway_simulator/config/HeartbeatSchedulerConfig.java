package uav_gateway_simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class HeartbeatSchedulerConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService heartbeatExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
}