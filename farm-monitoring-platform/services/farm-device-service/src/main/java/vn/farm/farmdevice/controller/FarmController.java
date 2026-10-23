package vn.farm.farmdevice.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.farm.contracts.farmdevice.api.FarmsApi;
import vn.farm.contracts.farmdevice.model.CreateFarmRequest;
import vn.farm.contracts.farmdevice.model.FarmDto;
import vn.farm.farmdevice.common.PageDto;
import vn.farm.farmdevice.common.Paging;
import vn.farm.farmdevice.dto.FarmDtos.UpdateRequest;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.mapper.FarmMapper;
import vn.farm.farmdevice.service.FarmService;

@RestController
@RequiredArgsConstructor
public class FarmController implements FarmsApi {
    private final FarmService farmService;

    @Override
    @PreAuthorize("@access.admin()")
    public ResponseEntity<FarmDto> createFarm(CreateFarmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FarmMapper.toDto(farmService.create(request)));
    }

    /** operationId getFarm đã có trong farm-device.v1.yaml. */
    @Override
    @PreAuthorize("@access.read()")
    public ResponseEntity<FarmDto> getFarm(UUID farmId) {
        return ResponseEntity.ok(FarmMapper.toDto(farmService.get(farmId)));
    }

    @GetMapping("/api/v1/farms")
    @PreAuthorize("@access.read()")
    public PageDto<FarmDto> listFarms(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        FarmStatus st = Paging.parseEnum(FarmStatus.class, status, "status");
        return PageDto.of(farmService.list(keyword, st, Paging.of(page, pageSize, Sort.by("name"))),
                FarmMapper::toDto);
    }

    @PatchMapping("/api/v1/farms/{farmId}")
    @PreAuthorize("@access.admin()")
    public FarmDto updateFarm(@PathVariable UUID farmId, @Valid @RequestBody UpdateRequest body) {
        return FarmMapper.toDto(farmService.update(farmId, body));
    }
}
