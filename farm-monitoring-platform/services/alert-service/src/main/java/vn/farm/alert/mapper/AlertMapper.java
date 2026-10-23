package vn.farm.alert.mapper;

import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.contracts.alert.model.AlertDto;
import vn.farm.contracts.alert.model.CreateAlertRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class AlertMapper {

    private AlertMapper() {
    }

    public static Alert toEntity(CreateAlertRequest request) {
        Alert alert = new Alert();

        alert.setNodeId(request.getNodeId());
        alert.setAlertType(request.getAlertType().trim());

        alert.setSeverity(
                AlertSeverity.valueOf(
                        request.getSeverity().name()
                )
        );

        alert.setMessage(request.getMessage().trim());

        return alert;
    }

    public static AlertDto toDto(Alert alert) {
        AlertDto dto = new AlertDto();

        dto.setAlertId(alert.getId());
        dto.setNodeId(alert.getNodeId());
        dto.setAlertType(alert.getAlertType());

        dto.setSeverity(
                AlertDto.SeverityEnum.valueOf(
                        alert.getSeverity().name()
                )
        );

        dto.setMessage(alert.getMessage());

        dto.setStatus(
                AlertDto.StatusEnum.valueOf(
                        alert.getStatus().name()
                )
        );

        if (alert.getCreatedAt() != null) {
            dto.setCreatedAt(
                    OffsetDateTime.ofInstant(
                            alert.getCreatedAt(),
                            ZoneOffset.UTC
                    )
            );
        }

        if (alert.getUpdatedAt() != null) {
            dto.setUpdatedAt(
                    OffsetDateTime.ofInstant(
                            alert.getUpdatedAt(),
                            ZoneOffset.UTC
                    )
            );
        }

        return dto;
    }
}
