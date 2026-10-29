package vn.farm.farmdevice.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farm.farmdevice.common.PageDto;
import vn.farm.farmdevice.common.Paging;
import vn.farm.farmdevice.dto.DeviceDtos.CreateUavRequest;
import vn.farm.farmdevice.dto.DeviceDtos.UavResponse;
import vn.farm.farmdevice.dto.DeviceDtos.UpdateUavRequest;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.service.UavService;

@RestController
@RequestMapping("/api/v1/uavs")
@RequiredArgsConstructor
public class UavController {
    private final UavService service;

    @GetMapping
    @PreAuthorize("@access.read()")
    public PageDto<UavResponse> list(@RequestParam(required = false) UUID farmId,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize) {
        UavStatus st = Paging.parseEnum(UavStatus.class, status, "status");
        return PageDto.of(service.list(farmId, st, Paging.of(page, pageSize, Sort.by("name"))), u -> u);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.deviceWrite()")
    public UavResponse create(@Valid @RequestBody CreateUavRequest body) {
        return service.create(body);
    }

    @GetMapping("/{uavId}")
    @PreAuthorize("@access.read()")
    public UavResponse get(@PathVariable UUID uavId) {
        return service.get(uavId);
    }

    @PatchMapping("/{uavId}")
    @PreAuthorize("@access.deviceWrite()")
    public UavResponse update(@PathVariable UUID uavId, @Valid @RequestBody UpdateUavRequest body) {
        return service.update(uavId, body);
    }
}
