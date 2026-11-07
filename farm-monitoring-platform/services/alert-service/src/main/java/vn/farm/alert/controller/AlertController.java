package vn.farm.alert.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.dto.response.AlertResponse;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.service.AlertService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    // Tạo cảnh báo
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    //@PreAuthorize("@alertAccess.canManage()")
    public AlertResponse createAlert(
            @Valid @RequestBody CreateAlertRequest request) {

        return alertService.createAlert(request);
    }

    // Lấy tất cả cảnh báo
    @GetMapping
    //@PreAuthorize("@alertAccess.canView()")
    public List<AlertResponse> getAllAlerts() {

        return alertService.getAllAlerts();
    }

    // Lấy cảnh báo theo ID
    @GetMapping("/{id}")
    //@PreAuthorize("@alertAccess.canView()")
    public AlertResponse getAlertById(
            @PathVariable UUID id) {

        return alertService.getAlertById(id);
    }

    // Lấy cảnh báo theo Node ID
    @GetMapping("/node/{nodeId}")
    //@PreAuthorize("@alertAccess.canView()")
    public List<AlertResponse> getAlertsByNodeId(
            @PathVariable UUID nodeId) {

        return alertService.getAlertsByNodeId(nodeId);
    }

    // Lọc cảnh báo theo trạng thái
    @GetMapping("/status/{status}")
    //@PreAuthorize("@alertAccess.canView()")
    public List<AlertResponse> getAlertsByStatus(
            @PathVariable AlertStatus status) {

        return alertService.getAlertsByStatus(status);
    }

    // Xác nhận cảnh báo
    @PatchMapping("/{id}/acknowledge")
    //@PreAuthorize("@alertAccess.canManage()")
    public AlertResponse acknowledgeAlert(
            @PathVariable UUID id) {

        return alertService.acknowledgeAlert(id);
    }

    // Đánh dấu cảnh báo đã xử lý
    @PatchMapping("/{id}/resolve")
    //@PreAuthorize("@alertAccess.canManage()")
    public AlertResponse resolveAlert(
            @PathVariable UUID id) {

        return alertService.resolveAlert(id);
    }
}
