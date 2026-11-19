package uav_gateway_simulator.config;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SimulatorInstanceIdentity {

    private final UUID instanceId = UUID.randomUUID();

    public UUID getInstanceId() {
        return instanceId;
    }
}