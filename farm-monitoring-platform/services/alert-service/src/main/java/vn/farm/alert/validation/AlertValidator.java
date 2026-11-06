package vn.farm.alert.validation;

import org.springframework.stereotype.Component;
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
            throw new IllegalArgumentException(
                    "nodeId không được null"
            );
        }

        if (alertType == null || alertType.isBlank()) {
            throw new IllegalArgumentException(
                    "alertType không được để trống"
            );
        }

        if (alertType.length() > 50) {
            throw new IllegalArgumentException(
                    "alertType tối đa 50 ký tự"
            );
        }

        if (severity == null) {
            throw new IllegalArgumentException(
                    "severity không được null"
            );
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "message không được để trống"
            );
        }

        if (message.length() > 500) {
            throw new IllegalArgumentException(
                    "message tối đa 500 ký tự"
            );
        }
    }

    public void validateAcknowledge(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException(
                    "Alert không tồn tại"
            );
        }

        if (alert.getStatus() != AlertStatus.NEW) {
            throw new IllegalStateException(
                    "Chỉ có thể xác nhận Alert ở trạng thái NEW"
            );
        }
    }

    public void validateResolve(Alert alert) {
        if (alert == null) {
            throw new IllegalArgumentException(
                    "Alert không tồn tại"
            );
        }

        if (alert.getStatus() != AlertStatus.ACKNOWLEDGED) {
            throw new IllegalStateException(
                    "Chỉ có thể xử lý Alert ở trạng thái ACKNOWLEDGED"
            );
        }
    }
}