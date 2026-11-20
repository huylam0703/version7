package uav_gateway_simulator.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uav_gateway_simulator.dto.EventEnvelope;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.service.MissionProcessingService;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionReadyConsumer {

    private final ObjectMapper objectMapper;
    private final MissionProcessingService missionProcessingService;

    @Value("${simulator.mission.enabled:false}")
    private boolean missionProcessingEnabled;

    @RabbitListener(queues = "simulator.mission.ready")
    public void receiveMission(String message) {

        EventEnvelope<MissionReadyPayload> event =
                objectMapper.readValue(
                        message,
                        objectMapper.getTypeFactory()
                                .constructParametricType(
                                        EventEnvelope.class,
                                        MissionReadyPayload.class
                                )
                );

        if (!"mission.ready".equals(event.eventType())) {
            log.warn(
                    "Ignoring unsupported event: {}",
                    event.eventType()
            );
            return;
        }

        MissionReadyPayload payload = event.payload();

        if (payload == null || payload.getExecutionId() == null) {
            log.warn("Invalid mission.ready payload");
            return;
        }

        if (!missionProcessingEnabled) {
            log.info(
                    "Mission processing disabled: executionId={}",
                    payload.getExecutionId()
            );
            return;
        }

        missionProcessingService.submit(payload);
    }
}