package uav_gateway_simulator.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class MissionReadyPayload {

    private UUID executionId;
    private UUID missionId;
    private UUID farmId;
    private UUID uavId;
    private UUID gatewayId;
    private Instant ackDeadline;
}
