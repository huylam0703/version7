
package vn.farm.alert.validation;

import org.springframework.stereotype.Component;
import vn.farm.core.BusinessException;
import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.entity.AlertSeverity;

import java.util.UUID;

@Component
public class AlertValidator {

    public void validateCreate(
            UUID nodeId,
            String alertType,
            AlertSeverity severity,
            String message
    ) {
        if (nodeId == null) {
            throw BusinessException.invalid(
                    "INVALID_NODE_ID",
                    "nodeId không được null"
            );
        }

        if (alertType == null || alertType.isBlank()) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_TYPE",
                    "alertType không được để trống"
            );
        }

        if (alertType.length() > 50) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_TYPE",
                    "alertType tối đa 50 ký tự"
            );
        }

        if (severity == null) {
            throw BusinessException.invalid(
                    "INVALID_SEVERITY",
                    "severity không được null"
            );
        }

        if (message == null || message.isBlank()) {
            throw BusinessException.invalid(
                    "INVALID_MESSAGE",
                    "message không được để trống"
            );
        }

        if (message.length() > 500) {
            throw BusinessException.invalid(
                    "INVALID_MESSAGE",
                    "message tối đa 500 ký tự"
            );
        }
    }

    public void validateAcknowledge(Alert alert) {
        if (alert == null) {
            throw BusinessException.notFound(
                    "ALERT_NOT_FOUND",
                    "Alert không tồn tại"
            );
        }

        if (alert.getStatus() != AlertStatus.NEW) {
            throw BusinessException.conflict(
                    "INVALID_STATUS_TRANSITION",
                    "Chỉ có thể xác nhận Alert ở trạng thái NEW"
            );
        }
    }

    public void validateResolve(Alert alert) {
        if (alert == null) {
            throw BusinessException.notFound(
                    "ALERT_NOT_FOUND",
                    "Alert không tồn tại"
            );
        }

        if (alert.getStatus() != AlertStatus.ACKNOWLEDGED) {
            throw BusinessException.conflict(
                    "INVALID_STATUS_TRANSITION",
                    "Chỉ có thể xử lý Alert ở trạng thái ACKNOWLEDGED"
            );
        }
    }
}
