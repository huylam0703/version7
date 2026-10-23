package vn.farm.farmdevice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import vn.farm.farmdevice.entity.NodeStatus;

public final class SensorDtos {
    private SensorDtos() {}

    public record TypeResponse(UUID typeId, String code, String name, String unit,
                               Double minValid, Double maxValid, Short decimals) {}

    public record ChannelRequest(
            @NotNull UUID typeId,
            @NotNull @Min(0) @Max(255) Integer channelNo,
            Double calibrationOffset) {}

    public record CreateNodeRequest(
            @NotNull UUID zoneId,
            @NotBlank @Size(max = 50) String deviceUid,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(min = 16, max = 72) String authKey,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Size(max = 20) String firmwareVer,
            LocalDate installedAt,
            @NotEmpty @Valid List<ChannelRequest> channels) {}

    public record UpdateNodeRequest(
            UUID zoneId,
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String name,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @Size(max = 20) String firmwareVer,
            NodeStatus status,
            LocalDate installedAt) {}

    public record RotateCredentialRequest(@NotBlank @Size(min = 16, max = 72) String newAuthKey) {}

    public record RotateCredentialResponse(UUID nodeId, boolean credentialRotated) {}

    public record ChannelResponse(UUID channelId, UUID typeId, String typeCode, Integer channelNo,
                                  Double calibrationOffset, boolean active) {}

    public record NodeResponse(
            UUID nodeId, UUID zoneId, UUID farmId, String deviceUid, String name,
            Double lat, Double lng, String firmwareVer, NodeStatus status, LocalDate installedAt,
            List<ChannelResponse> channels, Instant createdAt, Instant updatedAt) {}
}
