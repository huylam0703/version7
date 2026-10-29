
package vn.farm.alert.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.farm.alert.entity.Alert;
import vn.farm.alert.mapper.AlertMapper;
import vn.farm.alert.repository.AlertRepository;
import vn.farm.contracts.alert.model.CreateAlertRequest;
import vn.farm.core.BusinessException;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional
    public Alert create(CreateAlertRequest request) {

        if (request == null) {
            throw BusinessException.invalid(
                    "INVALID_REQUEST",
                    "Request không được để trống"
            );
        }

        if (request.getNodeId() == null) {
            throw BusinessException.invalid(
                    "INVALID_NODE_ID",
                    "NodeId không được để trống"
            );
        }

        if (request.getAlertType() == null
                || request.getAlertType().isBlank()) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_TYPE",
                    "Loại cảnh báo không được để trống"
            );
        }

        if (request.getAlertType().trim().length() > 50) {
            throw BusinessException.invalid(
                    "INVALID_ALERT_TYPE",
                    "Loại cảnh báo tối đa 50 ký tự"
            );
        }

        if (request.getSeverity() == null) {
            throw BusinessException.invalid(
                    "INVALID_SEVERITY",
                    "Mức độ cảnh báo không được để trống"
            );
        }

        if (request.getMessage() == null
                || request.getMessage().isBlank()) {
            throw BusinessException.invalid(
                    "INVALID_MESSAGE",
                    "Nội dung cảnh báo không được để trống"
            );
        }

        if (request.getMessage().trim().length() > 500) {
            throw BusinessException.invalid(
                    "INVALID_MESSAGE",
                    "Nội dung cảnh báo tối đa 500 ký tự"
            );
        }

        Alert alert = AlertMapper.toEntity(request);

        return alertRepository.save(alert);
    }
}
