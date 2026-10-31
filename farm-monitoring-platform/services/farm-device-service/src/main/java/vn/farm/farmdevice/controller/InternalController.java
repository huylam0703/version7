package vn.farm.farmdevice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.farm.farmdevice.dto.InternalDtos.InternalGateway;
import vn.farm.farmdevice.dto.InternalDtos.InternalNode;
import vn.farm.farmdevice.dto.InternalDtos.MissionCheckRequest;
import vn.farm.farmdevice.dto.InternalDtos.MissionCheckResponse;
import vn.farm.farmdevice.service.InternalLookupService;

/**
 * Chỉ service-to-service. FarmSecurityAutoConfiguration đã yêu cầu role SERVICE cho /internal/**;
 * gateway không route ra ngoài. Đường dẫn và response là ĐỀ XUẤT, cần thống nhất với nhóm sensor-data và mission.
 */
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalController {
    private final InternalLookupService service;

    @GetMapping("/nodes/by-device-uid/{deviceUid}")
    public InternalNode nodeByDeviceUid(@PathVariable String deviceUid) {
        return service.nodeByDeviceUid(deviceUid);
    }

    @GetMapping("/gateways/by-gateway-uid/{gatewayUid}")
    public InternalGateway gatewayByUid(@PathVariable String gatewayUid) {
        return service.gatewayByUid(gatewayUid);
    }

    @PostMapping("/references/mission-check")
    public MissionCheckResponse missionCheck(@Valid @RequestBody MissionCheckRequest body) {
        return service.checkMissionReferences(body);
    }
}
