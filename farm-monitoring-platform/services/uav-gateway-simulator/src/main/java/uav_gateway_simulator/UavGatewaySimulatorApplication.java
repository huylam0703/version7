package uav_gateway_simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import uav_gateway_simulator.config.AuthProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
@EnableScheduling
public class UavGatewaySimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(UavGatewaySimulatorApplication.class, args);
	}

}
