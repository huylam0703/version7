package vn.farm.alert.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import vn.farm.core.api.ApiResponse;
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

    @PostMapping
    public ResponseEntity<ApiResponse<AlertResponse>> createAlert(
            @Valid @RequestBody CreateAlertRequest request) {

        AlertResponse result = alertService.createAlert(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<AlertResponse>builder()
                        .message("Alert created successfully")
                        .result(result)
                        .build());
    }

    @GetMapping
    public ApiResponse<List<AlertResponse>> getAllAlerts() {
        return ApiResponse.<List<AlertResponse>>builder()
                .message("Get alerts successfully")
                .result(alertService.getAllAlerts())
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertResponse> getAlertById(
            @PathVariable UUID id) {

        return ApiResponse.<AlertResponse>builder()
                .message("Get alert successfully")
                .result(alertService.getAlertById(id))
                .build();
    }

    @GetMapping("/node/{nodeId}")
    public ApiResponse<List<AlertResponse>> getAlertsByNodeId(
            @PathVariable UUID nodeId) {

        return ApiResponse.<List<AlertResponse>>builder()
                .message("Get node alerts successfully")
                .result(alertService.getAlertsByNodeId(nodeId))
                .build();
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<AlertResponse>> getAlertsByStatus(
            @PathVariable AlertStatus status) {

        return ApiResponse.<List<AlertResponse>>builder()
                .message("Get alerts by status successfully")
                .result(alertService.getAlertsByStatus(status))
                .build();
    }

    // Lọc Alert theo Node ID và trạng thái
    @GetMapping("/node/{nodeId}/status/{status}")
    public ApiResponse<List<AlertResponse>> getAlertsByNodeIdAndStatus(
            @PathVariable UUID nodeId,
            @PathVariable AlertStatus status
    ) {
        return ApiResponse.<List<AlertResponse>>builder()
                .message("Get alerts by node and status successfully")
                .result(alertService.getAlertsByNodeIdAndStatus(nodeId, status))
                .build();
    }

    @PatchMapping("/{id}/acknowledge")
    public ApiResponse<AlertResponse> acknowledgeAlert(
            @PathVariable UUID id) {

        return ApiResponse.<AlertResponse>builder()
                .message("Alert acknowledged successfully")
                .result(alertService.acknowledgeAlert(id))
                .build();
    }

    @PatchMapping("/{id}/resolve")
    public ApiResponse<AlertResponse> resolveAlert(
            @PathVariable UUID id) {

        return ApiResponse.<AlertResponse>builder()
                .message("Alert resolved successfully")
                .result(alertService.resolveAlert(id))
                .build();
    }
}
