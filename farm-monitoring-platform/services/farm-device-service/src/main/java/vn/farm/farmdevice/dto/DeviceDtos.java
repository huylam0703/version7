package vn.farm.farmdevice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.entity.UavStatus;

public final class DeviceDtos {
    private DeviceDtos() {}

    // ---- UAV ----
    public record CreateUavRequest(
            @NotNull UUID farmId,
            @NotBlank @Size(max = 50) String serialNo,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String model,
            @Positive Short maxFlightMin) {}

    public record UpdateUavRequest(
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String name,
            @Size(max = 100) String model,
            @Positive Short maxFlightMin,
            UavStatus status) {}

    public record UavResponse(UUID uavId, UUID farmId, String serialNo, String name, String model,
                              Short maxFlightMin, UavStatus status, Instant createdAt, Instant updatedAt) {}

    // ---- Gateway ----
    public record CreateGatewayRequest(
            @NotBlank @Size(max = 50) String gatewayUid,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(min = 16, max = 72) String authKey,
            UUID uavId,
            @Size(max = 100) String hwModel,
            @Size(max = 20) String firmwareVer,
            @Positive Integer storageMb) {}

    /** uavId = gắn sang UAV khác; unassignUav = true để gỡ khỏi UAV (không dùng cùng lúc). */
    public record UpdateGatewayRequest(
            @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "không được để trống") String name,
            UUID uavId,
            Boolean unassignUav,
            @Size(max = 100) String hwModel,
            @Size(max = 20) String firmwareVer,
            @Positive Integer storageMb,
            GatewayStatus status) {}

    public record GatewayResponse(UUID gatewayId, UUID uavId, String gatewayUid, String name, String hwModel,
                                  String firmwareVer, Integer storageMb, GatewayStatus status, Instant createdAt) {}
}
