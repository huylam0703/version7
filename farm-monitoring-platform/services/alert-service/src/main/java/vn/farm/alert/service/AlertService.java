package vn.farm.alert.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.farm.alert.dto.event.AlertCreatedPayload;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.dto.response.AlertResponse;
import vn.farm.alert.entity.Alert;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.AlertStatus;
import vn.farm.alert.mapper.AlertMapper;
import vn.farm.alert.outbox.AlertOutboxService;
import vn.farm.alert.repository.AlertRepository;
import vn.farm.alert.validation.AlertValidator;
import vn.farm.core.BusinessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    private final AlertValidator alertValidator;
    private final AlertOutboxService alertOutboxService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AlertResponse createAlert(CreateAlertRequest request) {
        alertValidator.validateCreate(
                request.getNodeId(),
                request.getMissionId(),
                request.getAlertType(),
                request.getSeverity(),
                request.getMessage()
        );

        if (request.getReadingId() != null
                && alertRepository.existsByReadingId(request.getReadingId())) {
            log.info("Skipping duplicate sensor alert: readingId={}", request.getReadingId());

            throw BusinessException.invalid(
                    "DUPLICATE_SENSOR_ALERT",
                    "Bản ghi cảm biến này đã tạo cảnh báo"
            );
        }

        if (request.getReadingId() == null
                && request.getMissionId() != null
                && alertRepository.existsByMissionIdAndAlertType(
                request.getMissionId(), request.getAlertType())) {
            throw BusinessException.invalid(
                    "DUPLICATE_MISSION_ALERT",
                    "Mission đã có cảnh báo này"
            );
        }

        Alert alert = alertMapper.toAlert(request);
        alert.setStatus(AlertStatus.NEW);
        alert.setCreatedAt(Instant.now());

// Task 8: Lưu ID bản ghi cảm biến tạo ra Alert
// Alert thủ công vẫn có readingId = null
        alert.setReadingId(request.getReadingId());

        Alert savedAlert = alertRepository.save(alert);

        AlertCreatedPayload payload = new AlertCreatedPayload(
                savedAlert.getId(),
                savedAlert.getAlertType(),
                savedAlert.getSeverity().name(),
                savedAlert.getMessage(),
                null,
                savedAlert.getReadingId(),
                savedAlert.getNodeId(),
                request.getZoneId(),
                savedAlert.getMissionId(),
                request.getTriggerValue(),
                request.getTriggeredAt() == null ? Instant.now() : request.getTriggeredAt()
        );

        alertOutboxService.saveAlertCreated(payload);

        return alertMapper.toAlertResponse(savedAlert);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts(
            UUID nodeId,
            UUID missionId,
            AlertStatus status,
            AlertSeverity severity,
            String alertType) {

        return alertRepository.findAll((root, query, criteria) -> {
                    List<Predicate> filters = new ArrayList<>();

                    if (nodeId != null) {
                        filters.add(criteria.equal(root.get("nodeId"), nodeId));
                    }
                    if (missionId != null) {
                        filters.add(criteria.equal(root.get("missionId"), missionId));
                    }
                    if (status != null) {
                        filters.add(criteria.equal(root.get("status"), status));
                    }
                    if (severity != null) {
                        filters.add(criteria.equal(root.get("severity"), severity));
                    }
                    if (alertType != null) {
                        filters.add(criteria.equal(root.get("alertType"), alertType));
                    }

                    return criteria.and(filters.toArray(Predicate[]::new));
                })
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlertById(UUID id) {
        return alertMapper.toAlertResponse(findAlertById(id, false));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByNodeId(UUID nodeId) {
        return alertRepository.findByNodeId(nodeId)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByStatus(AlertStatus status) {
        return alertRepository.findByStatus(status)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlertsByNodeIdAndStatus(UUID nodeId, AlertStatus status) {
        return alertRepository.findByNodeIdAndStatus(nodeId, status)
                .stream()
                .map(alertMapper::toAlertResponse)
                .toList();
    }

    @Transactional
    public AlertResponse updateStatus(UUID id, AlertStatus newStatus) {
        Alert alert = findAlertById(id, true);

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

    @Transactional
    public AlertResponse acknowledgeAlert(UUID id) {
        return updateStatus(id, AlertStatus.ACKNOWLEDGED);
    }

    @Transactional
    public AlertResponse resolveAlert(UUID id) {
        return updateStatus(id, AlertStatus.RESOLVED);
    }

    private Alert findAlertById(UUID id, boolean forUpdate) {
        if (id == null) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_ID",
                    "Alert ID không được null"
            );
        }

        Optional<Alert> alert = forUpdate
                ? alertRepository.findForUpdateById(id)
                : alertRepository.findById(id);

        return alert.orElseThrow(() -> BusinessException.notFound(
                "ALERT_NOT_FOUND",
                "Không tìm thấy Alert với ID: " + id
        ));
    }
}