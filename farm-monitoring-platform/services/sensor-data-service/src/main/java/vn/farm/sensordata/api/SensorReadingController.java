package vn.farm.sensordata.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import vn.farm.sensordata.api.dto.SensorReadingRequest;
import vn.farm.sensordata.application.SensorReadingService;
import vn.farm.sensordata.domain.SensorReading;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sensor-readings")
public class SensorReadingController {

    private final SensorReadingService service;

    public SensorReadingController(SensorReadingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SensorReading>> getReadings(
            @RequestParam(defaultValue = "100") int limit) {

        return ResponseEntity.ok(service.getLatestReadings(limit));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createReading(
            @Valid @RequestBody SensorReadingRequest request) {

        Map<String, Object> result = service.saveReading(request);

        if ("DUPLICATE".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(201).body(result);
    }
}
