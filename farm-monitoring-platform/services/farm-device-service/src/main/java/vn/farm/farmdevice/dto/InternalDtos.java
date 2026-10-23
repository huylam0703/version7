package vn.farm.farmdevice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.entity.NodeStatus;

/** DTO cho /internal/v1 (chỉ service-to-service, có role SERVICE). */
public final class InternalDtos {
    private InternalDtos() {}

    public record InternalChannel(UUID channelId, UUID typeId, String typeCode, Integer channelNo,
                                  Double calibrationOffset, boolean active, Double minValid, Double maxValid) {}

    /** sensor-data-service dùng để xác thực node (F18) và tra kênh đo. authKeyHash để service kia tự so khớp. */
    public record InternalNode(UUID nodeId, UUID zoneId, UUID farmId, String deviceUid, NodeStatus status,
                               String authKeyHash, List<InternalChannel> channels) {}

    public record InternalGateway(UUID gatewayId, UUID uavId, String gatewayUid, GatewayStatus status,
                                  String authKeyHash) {}

    public record MissionCheckRequest(@NotNull UUID farmId, @NotNull UUID uavId, @NotNull UUID gatewayId,
                                      @NotNull List<UUID> nodeIds) {}

    public record Problem(String field, String code, String message) {}

    public record MissionCheckResponse(boolean valid, List<Problem> problems) {}
}
