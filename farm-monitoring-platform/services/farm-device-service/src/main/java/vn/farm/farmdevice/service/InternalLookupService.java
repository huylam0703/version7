package vn.farm.farmdevice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.dto.InternalDtos.InternalChannel;
import vn.farm.farmdevice.dto.InternalDtos.InternalGateway;
import vn.farm.farmdevice.dto.InternalDtos.InternalNode;
import vn.farm.farmdevice.dto.InternalDtos.MissionCheckRequest;
import vn.farm.farmdevice.dto.InternalDtos.MissionCheckResponse;
import vn.farm.farmdevice.dto.InternalDtos.Problem;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.entity.Gateway;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.SensorNode;
import vn.farm.farmdevice.entity.Uav;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.repository.FarmRepository;
import vn.farm.farmdevice.repository.GatewayRepository;
import vn.farm.farmdevice.repository.SensorNodeRepository;
import vn.farm.farmdevice.repository.UavRepository;

/** Phục vụ service khác gọi vào /internal/v1 (sensor-data tra node, mission kiểm tra tham chiếu). */
@Service
@RequiredArgsConstructor
public class InternalLookupService {
    private final SensorNodeRepository nodes;
    private final GatewayRepository gateways;
    private final FarmRepository farms;
    private final UavRepository uavs;

    @Transactional(readOnly = true)
    public InternalNode nodeByDeviceUid(String deviceUid) {
        SensorNode n = nodes.findByDeviceUidIgnoreCase(deviceUid.trim())
                .orElseThrow(() -> BusinessException.notFound("NODE_NOT_FOUND", "Node chưa đăng ký."));
        List<InternalChannel> channels = n.getChannels().stream()
                .map(c -> new InternalChannel(c.getId(), c.getType().getId(), c.getType().getCode(),
                        c.getChannelNo() == null ? null : c.getChannelNo().intValue(), c.getCalibrationOffset(),
                        c.isActive(), c.getType().getMinValid(), c.getType().getMaxValid()))
                .toList();
        return new InternalNode(n.getId(), n.getZone().getId(), n.getZone().getFarm().getId(), n.getDeviceUid(),
                n.getStatus(), n.getAuthKeyHash(), channels);
    }

    @Transactional(readOnly = true)
    public InternalGateway gatewayByUid(String gatewayUid) {
        Gateway g = gateways.findByGatewayUidIgnoreCase(gatewayUid.trim())
                .orElseThrow(() -> BusinessException.notFound("GATEWAY_NOT_FOUND", "Gateway chưa đăng ký."));
        return new InternalGateway(g.getId(), g.getUav() == null ? null : g.getUav().getId(), g.getGatewayUid(),
                g.getStatus(), g.getAuthKeyHash());
    }

    /**
     * mission-service gọi trước khi tạo nhiệm vụ (VAL-16, VAL-24). Luôn trả 200; valid=false kèm danh sách lỗi
     * để mission-service tự chọn mã HTTP (404 REFERENCE_NOT_FOUND hoặc 409 UAV_OR_GATEWAY_BUSY).
     */
    @Transactional(readOnly = true)
    public MissionCheckResponse checkMissionReferences(MissionCheckRequest r) {
        List<Problem> problems = new ArrayList<>();

        Farm farm = farms.findById(r.farmId()).orElse(null);
        if (farm == null) problems.add(new Problem("farmId", "FARM_NOT_FOUND", "Không tìm thấy nông trại."));
        else if (farm.getStatus() != FarmStatus.ACTIVE)
            problems.add(new Problem("farmId", "FARM_INACTIVE", "Nông trại đã ngưng hoạt động."));

        Uav uav = uavs.findById(r.uavId()).orElse(null);
        if (uav == null) problems.add(new Problem("uavId", "UAV_NOT_FOUND", "Không tìm thấy UAV."));
        else {
            if (farm != null && !uav.getFarm().getId().equals(farm.getId()))
                problems.add(new Problem("uavId", "UAV_NOT_IN_FARM", "UAV không thuộc nông trại đã chọn."));
            if (uav.getStatus() != UavStatus.READY)
                problems.add(new Problem("uavId", "UAV_BUSY", "UAV không ở trạng thái READY."));
        }

        Gateway gw = gateways.findById(r.gatewayId()).orElse(null);
        if (gw == null) problems.add(new Problem("gatewayId", "GATEWAY_NOT_FOUND", "Không tìm thấy gateway."));
        else {
            if (gw.getStatus() != GatewayStatus.READY)
                problems.add(new Problem("gatewayId", "GATEWAY_BUSY", "Gateway không ở trạng thái READY."));
            if (uav != null && (gw.getUav() == null || !gw.getUav().getId().equals(uav.getId())))
                problems.add(new Problem("gatewayId", "GATEWAY_NOT_ON_UAV", "Gateway chưa được gắn vào UAV đã chọn."));
        }

        Map<UUID, SensorNode> found = nodes.findAllById(r.nodeIds()).stream()
                .collect(Collectors.toMap(SensorNode::getId, Function.identity()));
        for (UUID id : r.nodeIds()) {
            SensorNode n = found.get(id);
            if (n == null) {
                problems.add(new Problem("nodeIds", "NODE_NOT_FOUND", "Không tìm thấy node " + id));
                continue;
            }
            if (farm != null && !n.getZone().getFarm().getId().equals(farm.getId()))
                problems.add(new Problem("nodeIds", "NODE_NOT_IN_FARM", "Node " + id + " không thuộc nông trại đã chọn."));
            if (n.getStatus() != NodeStatus.ACTIVE)
                problems.add(new Problem("nodeIds", "NODE_NOT_ACTIVE", "Node " + id + " không ở trạng thái ACTIVE."));
        }
        return new MissionCheckResponse(problems.isEmpty(), problems);
    }
}
