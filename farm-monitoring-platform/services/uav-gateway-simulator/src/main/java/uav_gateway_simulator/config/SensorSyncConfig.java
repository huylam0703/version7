package uav_gateway_simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class SensorSyncConfig {

    @Bean(defaultCandidate = false)
    ThreadPoolTaskScheduler sensorSyncScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("sensor-sync-");
        return scheduler;
    }
}
