package vn.farm.alert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.dto.response.AlertResponse;
import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.mapper.AlertMapper;
import vn.farm.alert.repository.AlertRepository;
import vn.farm.alert.validation.AlertValidator;
import vn.farm.core.BusinessException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    private final AlertValidator alertValidator;

    // 1. Tạo Alert
    @Transactional
    public AlertResponse createAlert(CreateAlertRequest request) {

        alertValidator.validateCreate(
                request.getNodeId(),
                request.getAlertType(),
                request.getSeverity(),
                request.getMessage()
        );

        Alert alert = alertMapper.toAlert(request);
        alert.setStatus(AlertStatus.NEW);

        Alert savedAlert = alertRepository.save(alert);

        return alertMapper.toAlertResponse(savedAlert);
    }

    // 2. Lấy tất cả Alert
    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts() {

        return alertRepository.findAll()
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    // 3. Lấy Alert theo ID
    @Transactional(readOnly = true)
    public AlertResponse getAlertById(UUID id) {

        Alert alert = findAlertById(id);

        return alertMapper.toAlertResponse(alert);
    }

    // 4. Lấy Alert theo Node ID
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByNodeId(UUID nodeId) {

        return alertRepository.findByNodeId(nodeId)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    // 5. Lấy Alert theo trạng thái
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByStatus(
            AlertStatus status
    ) {

        return alertRepository.findByStatus(status)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }
    // 6. Lọc Alert theo Node ID và trạng thái
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByNodeIdAndStatus(
            UUID nodeId,
            AlertStatus status
    ) {
        return alertRepository.findByNodeIdAndStatus(nodeId, status)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }
    // 7. Cập nhật trạng thái Alert
    @Transactional
    public AlertResponse updateStatus(
            UUID id,
            AlertStatus newStatus
    ) {

        Alert alert = findAlertById(id);

        if (newStatus == null) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_STATUS",
                    "Trạng thái Alert không được null"
            );
        }

        if (newStatus == AlertStatus.ACKNOWLEDGED) {

            alertValidator.validateAcknowledge(alert);

        } else if (newStatus == AlertStatus.RESOLVED) {

            alertValidator.validateResolve(alert);

        } else {
            throw BusinessException.invalid(
                    "INVALID_STATUS_TRANSITION",
                    "Không thể chuyển Alert sang trạng thái: " + newStatus
            );
        }

        alert.setStatus(newStatus);

        Alert savedAlert = alertRepository.save(alert);

        return alertMapper.toAlertResponse(savedAlert);
    }

    // 8. Xác nhận Alert
    @Transactional
    public AlertResponse acknowledgeAlert(UUID id) {

        return updateStatus(
                id,
                AlertStatus.ACKNOWLEDGED
        );
    }

    // 9. Đánh dấu Alert đã xử lý
    @Transactional
    public AlertResponse resolveAlert(UUID id) {

        return updateStatus(
                id,
                AlertStatus.RESOLVED
        );
    }

    // 10. Tìm Alert theo ID (dùng chung)
    private Alert findAlertById(UUID id) {

        if (id == null) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_ID",
                    "Alert ID không được null"
            );
        }

        return alertRepository.findById(id)
                .orElseThrow(() ->
                        BusinessException.notFound(
                                "ALERT_NOT_FOUND",
                                "Không tìm thấy Alert với ID: " + id
                        )
                );
    }
}