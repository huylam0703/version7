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
import vn.farm.farmdevice.dto.SensorDtos.CreateNodeRequest;
import vn.farm.farmdevice.dto.SensorDtos.NodeResponse;
import vn.farm.farmdevice.dto.SensorDtos.RotateCredentialRequest;
import vn.farm.farmdevice.dto.SensorDtos.RotateCredentialResponse;
import vn.farm.farmdevice.dto.SensorDtos.UpdateNodeRequest;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.service.SensorNodeService;

/** Route theo PLATFORM-CONVENTIONS §8: /api/v1/nodes. */
@RestController
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
public class SensorNodeController {
    private final SensorNodeService service;

    @GetMapping
    @PreAuthorize("@access.read()")
    public PageDto<NodeResponse> list(@RequestParam(required = false) UUID farmId,
                                      @RequestParam(required = false) UUID zoneId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int pageSize) {
        NodeStatus st = Paging.parseEnum(NodeStatus.class, status, "status");
        return PageDto.of(service.list(farmId, zoneId, st, keyword, Paging.of(page, pageSize, Sort.by("name"))), n -> n);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@access.deviceWrite()")
    public NodeResponse create(@Valid @RequestBody CreateNodeRequest body) {
        return service.create(body);
    }

    @GetMapping("/{nodeId}")
    @PreAuthorize("@access.read()")
    public NodeResponse get(@PathVariable UUID nodeId) {
        return service.get(nodeId);
    }

    @PatchMapping("/{nodeId}")
    @PreAuthorize("@access.deviceWrite()")
    public NodeResponse update(@PathVariable UUID nodeId, @Valid @RequestBody UpdateNodeRequest body) {
        return service.update(nodeId, body);
    }

    @PostMapping("/{nodeId}/rotate-credential")
    @PreAuthorize("@access.deviceWrite()")
    public RotateCredentialResponse rotate(@PathVariable UUID nodeId, @Valid @RequestBody RotateCredentialRequest body) {
        return service.rotateCredential(nodeId, body.newAuthKey());
    }
}
