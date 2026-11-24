package vn.farm.sensordata.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.farm.sensordata.api.dto.*;
import vn.farm.sensordata.api.dto.ReadingResults.*;
import vn.farm.sensordata.application.ReadingQueryService;
import vn.farm.sensordata.application.SensorReadingService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Sensor Readings v1")
@ApiResponse(responseCode = "400", description = "Malformed body or query",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class ReadingV1Controller {
    private final SensorReadingService ingest;
    private final ReadingQueryService query;

    public ReadingV1Controller(SensorReadingService ingest, ReadingQueryService query) {
        this.ingest = ingest;
        this.query = query;
    }

    @PostMapping("/readings")
    @Operation(summary = "Ingest a sensor reading", description = "Uses UTC timestamps. Validation results are stored with the reading.")
    @ApiResponse(responseCode = "201", description = "Stored as VALID or INVALID")
    @ApiResponse(responseCode = "200", description = "Existing reading was detected")
    public ResponseEntity<Written> create(@Valid @RequestBody ReadingSubmission request) {
        Written result = ingest.saveReading(request);
        return ResponseEntity.status(result.status() == WriteStatus.DUPLICATE ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @PostMapping({"/readings/batch", "/sync-batches"})
    @Operation(summary = "Ingest a batch of sensor readings", description =
            "Checks the request before saving. Returns an outcome for each reading in its original order.")
    public Batch batch(@Valid @RequestBody ReadingBatchRequest request) {
        return ingest.saveBatch(request.readings());
    }

    @GetMapping("/readings")
    @Operation(summary = "List sensor readings", description = "Combines supplied filters with AND. Pages start at 1.")
    public Page list(@ParameterObject @Valid @ModelAttribute ReadingFilter filter,
                     @ParameterObject @Valid @ModelAttribute ReadingPageRequest page) {
        return query.list(filter, page, false);
    }

    @GetMapping("/readings/{id}")
    @Operation(summary = "Get one sensor reading")
    @ApiResponse(responseCode = "404", description = "Reading was not found",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ReadingView detail(@PathVariable long id) {
        return query.getReading(id);
    }

    @GetMapping("/readings/nodes/{nodeId}")
    @Operation(summary = "Get a sensor node's reading history")
    public Page nodeHistory(@PathVariable String nodeId,
                            @ParameterObject @Valid @ModelAttribute ReadingFilter filter,
                            @ParameterObject @Valid @ModelAttribute ReadingPageRequest page) {
        return query.nodeHistory(nodeId, filter, page);
    }

    @GetMapping("/readings/latest")
    @Operation(summary = "Get the latest reading per node and sensor type",
            description = "Applies filters before selecting the most recent measuredAt, with id as a tiebreaker.")
    public Page latest(@ParameterObject @Valid @ModelAttribute ReadingFilter filter,
                       @ParameterObject @Valid @ModelAttribute ReadingPageRequest page) {
        return query.list(filter, page, true);
    }

    @GetMapping("/readings/statistics")
    @Operation(summary = "Calculate min, max, average and count", description =
            "Groups by sensor type, unit, value kind and ADC resolution. Defaults to VALID readings.")
    public List<Statistics> statistics(@ParameterObject @Valid @ModelAttribute ReadingFilter filter) {
        return query.statistics(filter);
    }

    @GetMapping("/readings/time-series")
    @Operation(summary = "Aggregate readings into UTC time buckets", description =
            "Requires from and to. Only buckets containing matching readings are returned. Defaults to VALID readings.")
    public List<TimePoint> timeSeries(@ParameterObject @Valid @ModelAttribute ReadingFilter filter,
                                      @RequestParam(defaultValue = "HOUR") ReadingInterval interval) {
        return query.timeSeries(filter, interval);
    }

    @GetMapping("/readings/invalid")
    @Operation(summary = "List invalid readings", description = "Always uses the INVALID validation status.")
    public Page invalid(@ParameterObject @Valid @ModelAttribute ReadingFilter filter,
                        @ParameterObject @Valid @ModelAttribute ReadingPageRequest page) {
        return query.list(filter.withStatus(ReadingFilter.Status.INVALID), page, false);
    }

    @GetMapping("/readings/quality")
    @Operation(summary = "Count valid readings, invalid readings and readings missing metadata",
            description = "Includes all validation statuses by default. invalidPercent is 0 when total is 0.")
    public Quality quality(@ParameterObject @Valid @ModelAttribute ReadingFilter filter) {
        return query.quality(filter);
    }
}
