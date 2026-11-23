package vn.farm.sensordata.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    @Operation(summary = "Get recent sensor readings", description = "Includes readings marked INVALID.")
    public ResponseEntity<List<SensorReading>> getReadings(
            @RequestParam(defaultValue = "100") int limit) {

        return ResponseEntity.ok(service.getLatestReadings(limit));
    }

    @PostMapping
    @Operation(summary = "Store a sensor reading", description =
            "Returns 201 for stored VALID or INVALID readings, 200 for duplicates, and 400 for malformed requests.")
    public ResponseEntity<Map<String, Object>> createReading(
            @Valid @RequestBody SensorReadingRequest request) {

        if (!Double.isFinite(request.sensorValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sensorValue must be finite");
        }

        Map<String, Object> result = service.saveReading(request);

        if ("DUPLICATE".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(201).body(result);
    }
}
