package vn.farm.farmdevice.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.dto.DeviceDtos.CreateGatewayRequest;
import vn.farm.farmdevice.dto.DeviceDtos.GatewayResponse;
import vn.farm.farmdevice.dto.DeviceDtos.UpdateGatewayRequest;
import vn.farm.farmdevice.entity.Gateway;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.GatewayRepository;

@Service
@RequiredArgsConstructor
public class GatewayService {
    private final GatewayRepository gateways;
    private final UavService uavService;
    private final PasswordEncoder encoder;

    @Transactional
    public GatewayResponse create(CreateGatewayRequest r) {
        String uid = r.gatewayUid().trim();
        if (gateways.existsByGatewayUidIgnoreCase(uid))
            throw BusinessException.conflict("GATEWAY_UID_EXISTS", "gatewayUid đã tồn tại.");

        Gateway g = new Gateway();
        g.setGatewayUid(uid);
        g.setName(r.name().trim());
        g.setAuthKeyHash(encoder.encode(r.authKey()));
        g.setHwModel(r.hwModel());
        g.setFirmwareVer(r.firmwareVer());
        g.setStorageMb(r.storageMb());
        if (r.uavId() != null) g.setUav(uavService.getEntity(r.uavId()));   // 404 UAV_NOT_FOUND
        return Mappers.toDto(gateways.save(g));
    }

    @Transactional(readOnly = true)
    public Page<GatewayResponse> list(UUID uavId, GatewayStatus status, Pageable pageable) {
        Specification<Gateway> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (uavId != null) ps.add(cb.equal(root.get("uav").get("id"), uavId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return gateways.findAll(spec, pageable).map(Mappers::toDto);
    }

    @Transactional(readOnly = true)
    public GatewayResponse get(UUID gatewayId) {
        return Mappers.toDto(getEntity(gatewayId));
    }

    /** Gateway đang IN_USE (đang chạy nhiệm vụ) thì không đổi UAV hay trạng thái tay (409 GATEWAY_BUSY). */
    @Transactional
    public GatewayResponse update(UUID gatewayId, UpdateGatewayRequest r) {
        Gateway g = getEntity(gatewayId);
        boolean busy = g.getStatus() == GatewayStatus.IN_USE;

        if (r.uavId() != null && Boolean.TRUE.equals(r.unassignUav()))
            throw BusinessException.invalid("VALIDATION_FAILED", "Không thể vừa gắn vừa gỡ UAV.");
        if (r.uavId() != null || Boolean.TRUE.equals(r.unassignUav())) {
            if (busy) throw BusinessException.conflict("GATEWAY_BUSY", "Gateway đang được dùng cho nhiệm vụ.");
            g.setUav(r.uavId() != null ? uavService.getEntity(r.uavId()) : null);
        }
        if (r.status() != null && r.status() != g.getStatus()) {
            if (r.status() == GatewayStatus.IN_USE)
                throw BusinessException.conflict("INVALID_STATE", "Trạng thái IN_USE do hệ thống nhiệm vụ cập nhật.");
            if (busy) throw BusinessException.conflict("GATEWAY_BUSY", "Gateway đang được dùng cho nhiệm vụ.");
            g.setStatus(r.status());
        }
        if (r.name() != null) g.setName(r.name().trim());
        if (r.hwModel() != null) g.setHwModel(r.hwModel());
        if (r.firmwareVer() != null) g.setFirmwareVer(r.firmwareVer());
        if (r.storageMb() != null) g.setStorageMb(r.storageMb());
        return Mappers.toDto(gateways.save(g));
    }

    @Transactional(readOnly = true)
    public Gateway getEntity(UUID gatewayId) {
        return gateways.findById(gatewayId)
                .orElseThrow(() -> BusinessException.notFound("GATEWAY_NOT_FOUND", "Không tìm thấy gateway."));
    }
}
