package vn.farm.farmdevice.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import vn.farm.contracts.farmdevice.api.FarmsApi;
import vn.farm.contracts.farmdevice.model.CreateFarmRequest;
import vn.farm.contracts.farmdevice.model.FarmDto;
import vn.farm.farmdevice.mapper.FarmMapper;
import vn.farm.farmdevice.service.FarmService;

@RestController
@RequiredArgsConstructor
public class FarmController implements FarmsApi {
    private final FarmService farmService;

    @Override
    //@PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FarmDto> createFarm(CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FarmMapper.toDto(farmService.create(request)));
    }

}