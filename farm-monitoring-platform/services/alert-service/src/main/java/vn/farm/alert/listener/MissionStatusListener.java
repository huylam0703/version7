package vn.farm.alert.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import vn.farm.alert.config.RabbitMQConfig;
import vn.farm.alert.dto.event.EventEnvelope;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.service.AlertService;
import vn.farm.core.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MissionStatusListener {

    private final AlertService alertService;

    @RabbitListener(queues = RabbitMQConfig.MISSION_STATUS_QUEUE)
    public void handleMissionStatus(EventEnvelope<MissionStatus> event) {
        if (event == null || event.version() != 1 || event.payload() == null) {
            return;
        }

        MissionStatus mission = event.payload();
        if (mission.missionId() == null) {
            return;
        }

        boolean failed = "FAILED".equals(mission.newStatus());
        if (!failed && !"PARTIAL".equals(mission.newStatus())) {
            return;
        }

        CreateAlertRequest request = new CreateAlertRequest();
        request.setMissionId(mission.missionId());
        request.setTriggeredAt(mission.changedAt());
        request.setAlertType(failed ? "MISSION_FAILED" : "COLLECTION_FAILED");
        request.setSeverity(failed ? AlertSeverity.CRITICAL : AlertSeverity.HIGH);
        request.setMessage("Mission " + mission.missionId() + " ended with status " + mission.newStatus());
        if (mission.failures() != null && mission.failures().size() == 1) {
            request.setNodeId(mission.failures().getFirst().nodeId());
        }

        try {
            alertService.createAlert(request);
        } catch (BusinessException exception) {
            if (!"DUPLICATE_MISSION_ALERT".equals(exception.getCode())) {
                throw exception;
            }
        }
    }

    public record MissionStatus(UUID missionId, String newStatus, Instant changedAt, List<NodeFailure> failures) {}

    public record NodeFailure(UUID nodeId) {}
}
