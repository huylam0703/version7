package vn.farm.mission.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatorProperties {

    private final String clientId;

    public SimulatorProperties(
            @Value("${integration.simulator.client-id}") String clientId
    ) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }
}