package vn.farm.alert.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import vn.farm.contracts.alert.api.AlertsApi;
import vn.farm.contracts.alert.model.CreateAlertRequest;
import vn.farm.contracts.alert.model.AlertDto;
import vn.farm.alert.mapper.AlertMapper;
import vn.farm.alert.service.AlertService;

@RestController
@RequiredArgsConstructor
public class AlertController implements AlertsApi {

    private final AlertService alertService;

    @Override
    public ResponseEntity<AlertDto> createAlert(
            CreateAlertRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                    AlertMapper.toDto(
                        alertService.create(request)
                    )
                );
    }
}