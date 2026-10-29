package vn.farm.farmdevice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import vn.farm.contracts.farmdevice.model.CreateFarmRequest;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.common.GeoRules;
import vn.farm.farmdevice.dto.FarmDtos.UpdateRequest;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.mapper.FarmMapper;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.FarmRepository;
import vn.farm.farmdevice.repository.UavRepository;
import vn.farm.starter.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class FarmService {
    private final FarmRepository farms;
    private final UavRepository uavs;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Transactional
    public Farm create(CreateFarmRequest request) {
        String name = request.getName().trim();
        if (name.isEmpty())
            throw BusinessException.invalid("VALIDATION_FAILED", "Tên nông trại không được để trống.");
        if (farms.existsByNameIgnoreCase(name))
            throw BusinessException.conflict("FARM_NAME_EXISTS", "Tên nông trại đã tồn tại.");

        GeoRules.checkPoint(request.getCenterLat(), request.getCenterLng());
        GeoRules.checkGeoJson(objectMapper, request.getBoundaryGeojson());   // tương đương CHECK ISJSON = 1, kiểm thêm Polygon

        Farm farm = FarmMapper.toEntity(request);
        farm.setCreatedBy(currentUser.id());
        return farms.save(farm);
    }

    @Transactional(readOnly = true)
    public Farm get(UUID farmId) {
        return farms.findById(farmId)
                .orElseThrow(() -> BusinessException.notFound("FARM_NOT_FOUND", "Không tìm thấy nông trại."));
    }

    /** Dùng cho thao tác ghi vào farm (tạo zone, UAV...): farm phải còn ACTIVE. */
    @Transactional(readOnly = true)
    public Farm getActive(UUID farmId) {
        Farm f = get(farmId);
        if (f.getStatus() != FarmStatus.ACTIVE)
            throw BusinessException.conflict("FARM_INACTIVE", "Nông trại đã ngưng hoạt động.");
        return f;
    }

    @Transactional(readOnly = true)
    public Page<Farm> list(String keyword, FarmStatus status, Pageable pageable) {
        Specification<Farm> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("address")), like)));
            }
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return farms.findAll(spec, pageable);
    }

    @Transactional
    public Farm update(UUID farmId, UpdateRequest r) {
        Farm f = get(farmId);

        if (r.name() != null) {
            String n = r.name().trim();
            if (!n.equalsIgnoreCase(f.getName()) && farms.existsByNameIgnoreCaseAndIdNot(n, farmId))
                throw BusinessException.conflict("FARM_NAME_EXISTS", "Tên nông trại đã tồn tại.");
            f.setName(n);
        }
        if (r.address() != null) f.setAddress(r.address());
        if (r.description() != null) f.setDescription(r.description());
        if (r.areaHa() != null) f.setAreaHa(Mappers.dec(r.areaHa()).setScale(2, java.math.RoundingMode.HALF_UP));
        if (r.centerLat() != null) f.setCenterLat(Mappers.dec(r.centerLat()));
        if (r.centerLng() != null) f.setCenterLng(Mappers.dec(r.centerLng()));
        GeoRules.checkPoint(f.getCenterLat(), f.getCenterLng());

        if (r.boundaryGeojson() != null) {
            GeoRules.checkGeoJson(objectMapper, r.boundaryGeojson());
            f.setBoundaryGeojson(r.boundaryGeojson());
        }
        if (r.status() != null) {
            if (r.status() == FarmStatus.INACTIVE && f.getStatus() == FarmStatus.ACTIVE
                    && uavs.existsByFarmIdAndStatus(farmId, UavStatus.IN_MISSION))
                throw BusinessException.conflict("RESOURCE_IN_USE", "Nông trại đang có UAV thực hiện nhiệm vụ.");
            f.setStatus(r.status());
        }
        return farms.save(f);
    }
}
