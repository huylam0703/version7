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
import vn.farm.farmdevice.dto.ZoneDtos.CreateRequest;
import vn.farm.farmdevice.dto.ZoneDtos.Response;
import vn.farm.farmdevice.dto.ZoneDtos.UpdateRequest;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.service.ZoneService;

@RestController
@RequiredArgsConstructor
public class ZoneController {
    private final ZoneService zoneService;

    @GetMapping("/api/v1/farms/{farmId}/zones")
    @PreAuthorize("@access.read()")
    public PageDto<Response> list(@PathVariable UUID farmId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int pageSize) {
        FarmStatus st = Paging.parseEnum(FarmStatus.class, status, "status");
        return PageDto.of(zoneService.list(farmId, st, Paging.of(page, pageSize, Sort.by("code"))), z -> z);
    }

    @PostMapping("/api/v1/farms/{farmId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.admin()")
    public Response create(@PathVariable UUID farmId, @Valid @RequestBody CreateRequest body) {
        return zoneService.create(farmId, body);
    }

    @GetMapping("/api/v1/zones/{zoneId}")
    @PreAuthorize("@access.read()")
    public Response get(@PathVariable UUID zoneId) {
        return zoneService.get(zoneId);
    }

    @PatchMapping("/api/v1/zones/{zoneId}")
    @PreAuthorize("@access.admin()")
    public Response update(@PathVariable UUID zoneId, @Valid @RequestBody UpdateRequest body) {
        return zoneService.update(zoneId, body);
    }
}
