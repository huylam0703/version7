package vn.farm.farmdevice.service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.dto.DeviceDtos.CreateUavRequest;
import vn.farm.farmdevice.dto.DeviceDtos.UavResponse;
import vn.farm.farmdevice.dto.DeviceDtos.UpdateUavRequest;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.entity.Uav;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.UavRepository;

@Service
@RequiredArgsConstructor
public class UavService {
    private final UavRepository uavs;
    private final FarmService farmService;

    @Transactional
    public UavResponse create(CreateUavRequest r) {
        Farm farm = farmService.getActive(r.farmId());
        String serial = r.serialNo().trim();
        if (uavs.existsBySerialNoIgnoreCase(serial))
            throw BusinessException.conflict("SERIAL_EXISTS", "Số serial đã tồn tại.");

        Uav u = new Uav();
        u.setFarm(farm);
        u.setSerialNo(serial);
        u.setName(r.name().trim());
        u.setModel(r.model());
        u.setMaxFlightMin(r.maxFlightMin());
        return Mappers.toDto(uavs.save(u));
    }

    @Transactional(readOnly = true)
    public Page<UavResponse> list(UUID farmId, UavStatus status, Pageable pageable) {
        Specification<Uav> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (farmId != null) ps.add(cb.equal(root.get("farm").get("id"), farmId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return uavs.findAll(spec, pageable).map(Mappers::toDto);
    }

    @Transactional(readOnly = true)
    public UavResponse get(UUID uavId) {
        return Mappers.toDto(getEntity(uavId));
    }

    /**
     * IN_MISSION chỉ do mission-service đặt (qua event mission.status_changed), không cho đặt tay.
     * UAV đang IN_MISSION thì không đổi trạng thái tay (409 UAV_BUSY).
     */
    @Transactional
    public UavResponse update(UUID uavId, UpdateUavRequest r) {
        Uav u = getEntity(uavId);
        if (r.name() != null) u.setName(r.name().trim());
        if (r.model() != null) u.setModel(r.model());
        if (r.maxFlightMin() != null) u.setMaxFlightMin(r.maxFlightMin());
        if (r.status() != null && r.status() != u.getStatus()) {
            if (r.status() == UavStatus.IN_MISSION)
                throw BusinessException.conflict("INVALID_STATE", "Trạng thái IN_MISSION do hệ thống nhiệm vụ cập nhật.");
            if (u.getStatus() == UavStatus.IN_MISSION)
                throw BusinessException.conflict("UAV_BUSY", "UAV đang thực hiện nhiệm vụ.");
            u.setStatus(r.status());
        }
        return Mappers.toDto(uavs.save(u));
    }

    @Transactional(readOnly = true)
    public Uav getEntity(UUID uavId) {
        return uavs.findById(uavId)
                .orElseThrow(() -> BusinessException.notFound("UAV_NOT_FOUND", "Không tìm thấy UAV."));
    }
}
