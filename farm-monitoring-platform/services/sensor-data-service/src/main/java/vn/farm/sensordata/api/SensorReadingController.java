package vn.farm.sensordata.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import vn.farm.sensordata.api.dto.ReadingResults.WriteStatus;
import vn.farm.sensordata.api.dto.ReadingResults.Written;
import vn.farm.sensordata.api.dto.SensorReadingRequest;
import vn.farm.sensordata.application.SensorReadingService;
import vn.farm.sensordata.domain.SensorReading;

import java.util.List;

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
    public ResponseEntity<Written> createReading(
            @Valid @RequestBody SensorReadingRequest request) {

        Written result = service.saveReading(request);

        if (result.status() == WriteStatus.DUPLICATE) {
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.status(201).body(result);
    }
}
