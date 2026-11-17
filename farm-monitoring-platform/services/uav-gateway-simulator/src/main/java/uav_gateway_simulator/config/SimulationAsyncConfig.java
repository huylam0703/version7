package uav_gateway_simulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class SimulationAsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService simulationExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}