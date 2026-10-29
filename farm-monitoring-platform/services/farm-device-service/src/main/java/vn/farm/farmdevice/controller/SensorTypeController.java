package vn.farm.farmdevice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.farm.farmdevice.dto.SensorDtos.TypeResponse;
import vn.farm.farmdevice.service.SensorTypeService;

@RestController
@RequiredArgsConstructor
public class SensorTypeController {
    private final SensorTypeService service;

    @GetMapping("/api/v1/sensor-types")
    @PreAuthorize("@access.read()")
    public List<TypeResponse> list() {
        return service.list();
    }
}
