package vn.farm.sensordata.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import vn.farm.sensordata.domain.ReadingValueKind;

import java.time.Instant;
import java.util.UUID;

public record ReadingFilter(
        @Size(max = 100) String nodeId,
        @Size(max = 50) String sensorType,
        UUID farmId,
        UUID zoneId,
        UUID missionId,
        UUID executionId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Schema(description = "Inclusive measuredAt in ISO 8601 UTC") Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Schema(description = "Exclusive measuredAt in ISO 8601 UTC") Instant to,
        Status validationStatus,
        ReadingValueKind valueKind,
        @Size(max = 20) String unit
) {
    public enum Status { VALID, INVALID }

    public ReadingFilter forNode(String id) {
        return new ReadingFilter(id, sensorType, farmId, zoneId, missionId, executionId,
                from, to, validationStatus, valueKind, unit);
    }

    public ReadingFilter withStatus(Status status) {
        return new ReadingFilter(nodeId, sensorType, farmId, zoneId, missionId, executionId,
                from, to, status, valueKind, unit);
    }
}
