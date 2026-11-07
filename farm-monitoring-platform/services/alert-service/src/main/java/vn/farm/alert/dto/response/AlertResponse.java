package vn.farm.alert.dto.response;

import lombok.Data;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.AlertStatus;

import java.util.UUID;

@Data
public class AlertResponse {

    private UUID id;

    private UUID nodeId;

    private String alertType;

    private AlertSeverity severity;

    private String message;

    private AlertStatus status;
}
