package vn.farm.farmdevice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import vn.farm.farmdevice.entity.FarmStatus;

public final class ZoneDtos {
    private ZoneDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String cropType,
            @DecimalMin("-90") @DecimalMax("90") Double centerLat,
            @DecimalMin("-180") @DecimalMax("180") Double centerLng,
            String boundaryGeojson) {}

    public record UpdateRequest(
            @Size(max = 30) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String code,
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String name,
            @Size(max = 100) String cropType,
            @DecimalMin("-90") @DecimalMax("90") Double centerLat,
            @DecimalMin("-180") @DecimalMax("180") Double centerLng,
            String boundaryGeojson,
            FarmStatus status) {}

    public record Response(
            UUID zoneId, UUID farmId, String code, String name, String cropType,
            Double centerLat, Double centerLng, String boundaryGeojson, FarmStatus status,
            Instant createdAt, Instant updatedAt) {}
}
