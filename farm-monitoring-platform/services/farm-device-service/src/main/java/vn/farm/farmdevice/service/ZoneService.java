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
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.common.GeoRules;
import vn.farm.farmdevice.dto.ZoneDtos.CreateRequest;
import vn.farm.farmdevice.dto.ZoneDtos.Response;
import vn.farm.farmdevice.dto.ZoneDtos.UpdateRequest;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.entity.FarmStatus;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.Zone;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.SensorNodeRepository;
import vn.farm.farmdevice.repository.ZoneRepository;

@Service
@RequiredArgsConstructor
public class ZoneService {
    private final ZoneRepository zones;
    private final SensorNodeRepository nodes;
    private final FarmService farmService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Response create(UUID farmId, CreateRequest r) {
        Farm farm = farmService.getActive(farmId);
        String code = r.code().trim();
        if (code.isEmpty()) throw BusinessException.invalid("VALIDATION_FAILED", "Mã khu vực không được để trống.");
        if (zones.existsByFarmIdAndCodeIgnoreCase(farmId, code))
            throw BusinessException.conflict("ZONE_CODE_EXISTS", "Mã khu vực đã tồn tại trong nông trại.");
        GeoRules.checkPoint(r.centerLat(), r.centerLng());
        GeoRules.checkGeoJson(objectMapper, r.boundaryGeojson());

        Zone z = new Zone();
        z.setFarm(farm);
        z.setCode(code);
        z.setName(r.name().trim());
        z.setCropType(r.cropType());
        z.setCenterLat(Mappers.dec(r.centerLat()));
        z.setCenterLng(Mappers.dec(r.centerLng()));
        z.setBoundaryGeojson(r.boundaryGeojson());
        return Mappers.toDto(zones.save(z));
    }

    @Transactional(readOnly = true)
    public Response get(UUID zoneId) {
        return Mappers.toDto(getEntity(zoneId));
    }

    @Transactional(readOnly = true)
    public Page<Response> list(UUID farmId, FarmStatus status, Pageable pageable) {
        farmService.get(farmId);   // 404 FARM_NOT_FOUND nếu không có
        Specification<Zone> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("farm").get("id"), farmId));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return zones.findAll(spec, pageable).map(Mappers::toDto);
    }

    @Transactional
    public Response update(UUID zoneId, UpdateRequest r) {
        Zone z = getEntity(zoneId);

        if (r.code() != null) {
            String code = r.code().trim();
            if (!code.equalsIgnoreCase(z.getCode())
                    && zones.existsByFarmIdAndCodeIgnoreCaseAndIdNot(z.getFarm().getId(), code, zoneId))
                throw BusinessException.conflict("ZONE_CODE_EXISTS", "Mã khu vực đã tồn tại trong nông trại.");
            z.setCode(code);
        }
        if (r.name() != null) z.setName(r.name().trim());
        if (r.cropType() != null) z.setCropType(r.cropType());
        if (r.centerLat() != null) z.setCenterLat(Mappers.dec(r.centerLat()));
        if (r.centerLng() != null) z.setCenterLng(Mappers.dec(r.centerLng()));
        GeoRules.checkPoint(z.getCenterLat(), z.getCenterLng());
        if (r.boundaryGeojson() != null) {
            GeoRules.checkGeoJson(objectMapper, r.boundaryGeojson());
            z.setBoundaryGeojson(r.boundaryGeojson());
        }
        if (r.status() != null) {
            if (r.status() == FarmStatus.INACTIVE && z.getStatus() == FarmStatus.ACTIVE
                    && nodes.existsByZoneIdAndStatusIn(zoneId, List.of(NodeStatus.ACTIVE, NodeStatus.MAINTENANCE)))
                throw BusinessException.conflict("RESOURCE_IN_USE", "Khu vực còn node cảm biến đang sử dụng.");
            z.setStatus(r.status());
        }
        return Mappers.toDto(zones.save(z));
    }

    @Transactional(readOnly = true)
    public Zone getEntity(UUID zoneId) {
        return zones.findById(zoneId)
                .orElseThrow(() -> BusinessException.notFound("ZONE_NOT_FOUND", "Không tìm thấy khu vực."));
    }
}
