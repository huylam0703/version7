package vn.farm.alert.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.farm.alert.entity.AlertSeverity;
import vn.farm.alert.entity.SensorThreshold;
import vn.farm.alert.repository.SensorThresholdRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/thresholds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SensorThresholdController {
    private final SensorThresholdRepository thresholds;

    @GetMapping
    public List<SensorThreshold> list() {
        return thresholds.findAll();
    }

    @PutMapping
    public SensorThreshold save(@Valid @RequestBody ThresholdRequest request) {
        if (!Double.isFinite(request.minValue()) || !Double.isFinite(request.maxValue())
                || request.minValue() > request.maxValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Threshold requires finite min <= max");
        }
        return thresholds.save(new SensorThreshold(request.farmId(), request.sensorCode(),
                request.minValue(), request.maxValue(), request.severity()));
    }

    public record ThresholdRequest(UUID farmId,
            @NotNull @Pattern(regexp = "AIR_TEMP|AIR_HUMIDITY|SOIL_MOISTURE|LIGHT|PH|WATER_LEVEL") String sensorCode,
            @NotNull Double minValue, @NotNull Double maxValue, @NotNull AlertSeverity severity) {}
}
