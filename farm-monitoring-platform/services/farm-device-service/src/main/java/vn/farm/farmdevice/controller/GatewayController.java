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
import vn.farm.farmdevice.dto.DeviceDtos.CreateGatewayRequest;
import vn.farm.farmdevice.dto.DeviceDtos.GatewayResponse;
import vn.farm.farmdevice.dto.DeviceDtos.UpdateGatewayRequest;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.service.GatewayService;

@RestController
@RequestMapping("/api/v1/gateways")
@RequiredArgsConstructor
public class GatewayController {
    private final GatewayService service;

    @GetMapping
    @PreAuthorize("@access.read()")
    public PageDto<GatewayResponse> list(@RequestParam(required = false) UUID uavId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        GatewayStatus st = Paging.parseEnum(GatewayStatus.class, status, "status");
        return PageDto.of(service.list(uavId, st, Paging.of(page, pageSize, Sort.by("name"))), g -> g);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.deviceWrite()")
    public GatewayResponse create(@Valid @RequestBody CreateGatewayRequest body) {
        return service.create(body);
    }

    @GetMapping("/{gatewayId}")
    @PreAuthorize("@access.read()")
    public GatewayResponse get(@PathVariable UUID gatewayId) {
        return service.get(gatewayId);
    }

    @PatchMapping("/{gatewayId}")
    @PreAuthorize("@access.deviceWrite()")
    public GatewayResponse update(@PathVariable UUID gatewayId, @Valid @RequestBody UpdateGatewayRequest body) {
        return service.update(gatewayId, body);
    }
}
