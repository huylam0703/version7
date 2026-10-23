package vn.farm.farmdevice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vn.farm.farmdevice.entity.FarmStatus;

public final class FarmDtos {
    private FarmDtos() {}

    /** PATCH: field null = giữ nguyên (chưa hỗ trợ xoá giá trị về null). */
    public record UpdateRequest(
            @Size(max = 150) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String name,
            @Size(max = 255) String address,
            @DecimalMin("-90") @DecimalMax("90") Double centerLat,
            @DecimalMin("-180") @DecimalMax("180") Double centerLng,
            @Positive Double areaHa,
            String boundaryGeojson,
            String description,
            FarmStatus status) {}
}
