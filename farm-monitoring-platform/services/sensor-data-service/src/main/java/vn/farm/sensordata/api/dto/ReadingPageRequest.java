package vn.farm.sensordata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReadingPageRequest(
        @Min(1) @Schema(defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Schema(defaultValue = "20") Integer pageSize,
        @Schema(defaultValue = "measuredAt", allowableValues = {
                "measuredAt", "collectedAt", "receivedAt", "sensorValue", "nodeId", "sensorType", "id"
        }) String sortBy,
        @Schema(defaultValue = "desc", allowableValues = {"asc", "desc"}) String sortDirection
) {
    public ReadingPageRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 20 : pageSize;
        sortBy = sortBy == null ? "measuredAt" : sortBy;
        sortDirection = sortDirection == null ? "desc" : sortDirection;
    }

    public long offset() {
        return (page - 1L) * pageSize;
    }
}
