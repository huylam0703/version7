package vn.farm.alert.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import vn.farm.alert.entity.AlertSeverity;

import java.util.UUID;

@Data
public class CreateAlertRequest {

    @NotNull(message = "Node ID không được để trống")
    private UUID nodeId;

    @NotBlank(message = "Loại cảnh báo không được để trống")
    @Size(max = 50)
    private String alertType;

    @NotNull(message = "Mức độ cảnh báo không được để trống")
    private AlertSeverity severity;

    @NotBlank(message = "Nội dung cảnh báo không được để trống")
    @Size(max = 500)
    private String message;
}
