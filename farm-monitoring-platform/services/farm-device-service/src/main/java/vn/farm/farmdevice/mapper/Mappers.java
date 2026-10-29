package vn.farm.farmdevice.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.farm.farmdevice.dto.DeviceDtos.GatewayResponse;
import vn.farm.farmdevice.dto.DeviceDtos.UavResponse;
import vn.farm.farmdevice.dto.SensorDtos.ChannelResponse;
import vn.farm.farmdevice.dto.SensorDtos.NodeResponse;
import vn.farm.farmdevice.dto.SensorDtos.TypeResponse;
import vn.farm.farmdevice.dto.ZoneDtos;
import vn.farm.farmdevice.entity.Gateway;
import vn.farm.farmdevice.entity.NodeChannel;
import vn.farm.farmdevice.entity.SensorNode;
import vn.farm.farmdevice.entity.SensorType;
import vn.farm.farmdevice.entity.Uav;
import vn.farm.farmdevice.entity.Zone;

/** Gọi trong transaction của service vì có đọc quan hệ lazy (open-in-view đã tắt). */
public final class Mappers {
    private Mappers() {}

    public static BigDecimal dec(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP);
    }

    public static Double dbl(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    public static ZoneDtos.Response toDto(Zone z) {
        return new ZoneDtos.Response(z.getId(), z.getFarm().getId(), z.getCode(), z.getName(), z.getCropType(),
                dbl(z.getCenterLat()), dbl(z.getCenterLng()), z.getBoundaryGeojson(), z.getStatus(),
                z.getCreatedAt(), z.getUpdatedAt());
    }

    public static TypeResponse toDto(SensorType t) {
        return new TypeResponse(t.getId(), t.getCode(), t.getName(), t.getUnit(),
                t.getMinValid(), t.getMaxValid(), t.getDecimals());
    }

    public static ChannelResponse toDto(NodeChannel c) {
        return new ChannelResponse(c.getId(), c.getType().getId(), c.getType().getCode(),
                c.getChannelNo() == null ? null : c.getChannelNo().intValue(),
                c.getCalibrationOffset(), c.isActive());
    }

    public static NodeResponse toDto(SensorNode n) {
        return new NodeResponse(n.getId(), n.getZone().getId(), n.getZone().getFarm().getId(), n.getDeviceUid(),
                n.getName(), dbl(n.getLat()), dbl(n.getLng()), n.getFirmwareVer(), n.getStatus(),
                n.getInstalledAt(), n.getChannels().stream().map(Mappers::toDto).toList(),
                n.getCreatedAt(), n.getUpdatedAt());
    }

    public static UavResponse toDto(Uav u) {
        return new UavResponse(u.getId(), u.getFarm().getId(), u.getSerialNo(), u.getName(), u.getModel(),
                u.getMaxFlightMin(), u.getStatus(), u.getCreatedAt(), u.getUpdatedAt());
    }

    public static GatewayResponse toDto(Gateway g) {
        return new GatewayResponse(g.getId(), g.getUav() == null ? null : g.getUav().getId(), g.getGatewayUid(),
                g.getName(), g.getHwModel(), g.getFirmwareVer(), g.getStorageMb(), g.getStatus(), g.getCreatedAt());
    }
}
