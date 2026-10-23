package vn.farm.farmdevice.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.common.GeoRules;
import vn.farm.farmdevice.dto.SensorDtos.ChannelRequest;
import vn.farm.farmdevice.dto.SensorDtos.CreateNodeRequest;
import vn.farm.farmdevice.dto.SensorDtos.NodeResponse;
import vn.farm.farmdevice.dto.SensorDtos.RotateCredentialResponse;
import vn.farm.farmdevice.dto.SensorDtos.UpdateNodeRequest;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.entity.NodeChannel;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.SensorNode;
import vn.farm.farmdevice.entity.SensorType;
import vn.farm.farmdevice.entity.Zone;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.SensorNodeRepository;
import vn.farm.farmdevice.repository.SensorTypeRepository;
import vn.farm.farmdevice.repository.ZoneRepository;

@Service
@RequiredArgsConstructor
public class SensorNodeService {
    private final SensorNodeRepository nodes;
    private final SensorTypeRepository types;
    private final ZoneRepository zones;
    private final PasswordEncoder encoder;

    @Transactional
    public NodeResponse create(CreateNodeRequest r) {
        String uid = r.deviceUid().trim();
        if (uid.isEmpty()) throw BusinessException.invalid("VALIDATION_FAILED", "deviceUid không được để trống.");
        if (nodes.existsByDeviceUidIgnoreCase(uid))
            throw BusinessException.conflict("DEVICE_UID_EXISTS", "deviceUid đã được đăng ký.");

        Zone zone = zones.findById(r.zoneId())
                .orElseThrow(() -> BusinessException.notFound("ZONE_OR_TYPE_NOT_FOUND", "Không tìm thấy khu vực."));
        if (zone.getStatus() != FarmStatus.ACTIVE)
            throw BusinessException.conflict("ZONE_INACTIVE", "Khu vực đã ngưng hoạt động.");
        GeoRules.checkPoint(r.lat(), r.lng());

        Set<UUID> typeIds = new HashSet<>();
        for (ChannelRequest c : r.channels()) {
            if (!typeIds.add(c.typeId()))
                throw BusinessException.invalid("VALIDATION_FAILED", "Mỗi loại cảm biến chỉ được có một kênh trên một node.");
        }
        Map<UUID, SensorType> typeMap = types.findAllById(typeIds).stream()
                .collect(Collectors.toMap(SensorType::getId, Function.identity()));
        if (typeMap.size() != typeIds.size())
            throw BusinessException.notFound("ZONE_OR_TYPE_NOT_FOUND", "Có loại cảm biến không tồn tại.");

        SensorNode n = new SensorNode();
        n.setZone(zone);
        n.setDeviceUid(uid);
        n.setName(r.name().trim());
        n.setAuthKeyHash(encoder.encode(r.authKey()));
        n.setLat(Mappers.dec(r.lat()));
        n.setLng(Mappers.dec(r.lng()));
        n.setFirmwareVer(r.firmwareVer());
        n.setInstalledAt(r.installedAt());
        for (ChannelRequest c : r.channels()) {
            NodeChannel ch = new NodeChannel();
            ch.setNode(n);
            ch.setType(typeMap.get(c.typeId()));
            ch.setChannelNo(c.channelNo().shortValue());
            ch.setCalibrationOffset(c.calibrationOffset() == null ? 0.0 : c.calibrationOffset());
            n.getChannels().add(ch);
        }
        return Mappers.toDto(nodes.save(n));
    }

    @Transactional(readOnly = true)
    public NodeResponse get(UUID nodeId) {
        return Mappers.toDto(getEntity(nodeId));
    }

    @Transactional(readOnly = true)
    public Page<NodeResponse> list(UUID farmId, UUID zoneId, NodeStatus status, String keyword, Pageable pageable) {
        Specification<SensorNode> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (farmId != null) ps.add(cb.equal(root.get("zone").get("farm").get("id"), farmId));
            if (zoneId != null) ps.add(cb.equal(root.get("zone").get("id"), zoneId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("deviceUid")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return nodes.findAll(spec, pageable).map(Mappers::toDto);
    }

    @Transactional
    public NodeResponse update(UUID nodeId, UpdateNodeRequest r) {
        SensorNode n = getEntity(nodeId);

        if (r.zoneId() != null && !r.zoneId().equals(n.getZone().getId())) {
            Zone zone = zones.findById(r.zoneId())
                    .orElseThrow(() -> BusinessException.notFound("NODE_OR_ZONE_NOT_FOUND", "Không tìm thấy khu vực."));
            if (zone.getStatus() != FarmStatus.ACTIVE)
                throw BusinessException.conflict("ZONE_INACTIVE", "Khu vực đã ngưng hoạt động.");
            n.setZone(zone);
        }
        if (r.name() != null) n.setName(r.name().trim());
        if (r.lat() != null) n.setLat(Mappers.dec(r.lat()));
        if (r.lng() != null) n.setLng(Mappers.dec(r.lng()));
        GeoRules.checkPoint(n.getLat(), n.getLng());
        if (r.firmwareVer() != null) n.setFirmwareVer(r.firmwareVer());
        if (r.installedAt() != null) n.setInstalledAt(r.installedAt());
        if (r.status() != null) n.setStatus(r.status());
        return Mappers.toDto(nodes.save(n));
    }

    /** Đổi khóa xác thực; chỉ lưu hash, không log khóa. */
    @Transactional
    public RotateCredentialResponse rotateCredential(UUID nodeId, String newAuthKey) {
        SensorNode n = getEntity(nodeId);
        n.setAuthKeyHash(encoder.encode(newAuthKey));
        nodes.save(n);
        return new RotateCredentialResponse(n.getId(), true);
    }

    @Transactional(readOnly = true)
    public SensorNode getEntity(UUID nodeId) {
        return nodes.findById(nodeId)
                .orElseThrow(() -> BusinessException.notFound("NODE_NOT_FOUND", "Không tìm thấy node cảm biến."));
    }
}
