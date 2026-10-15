package vn.farm.farmdevice.mapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import vn.farm.contracts.farmdevice.model.CreateFarmRequest;
import vn.farm.contracts.farmdevice.model.FarmDto;
import vn.farm.farmdevice.entity.Farm;

public final class FarmMapper {
    private FarmMapper() {}

    public static Farm toEntity(CreateFarmRequest r) {
        Farm f = new Farm();
        f.setName(r.getName().trim());
        f.setAddress(r.getAddress());
        f.setCenterLat(r.getCenterLat() == null ? null : BigDecimal.valueOf(r.getCenterLat()));
        f.setCenterLng(r.getCenterLng() == null ? null : BigDecimal.valueOf(r.getCenterLng()));
        f.setAreaHa(r.getAreaHa() == null ? null : BigDecimal.valueOf(r.getAreaHa()));
        f.setBoundaryGeojson(r.getBoundaryGeojson());
        f.setDescription(r.getDescription());
        return f;
    }

    public static FarmDto toDto(Farm f) {
        FarmDto d = new FarmDto();
        d.setFarmId(f.getId());
        d.setName(f.getName());
        d.setAddress(f.getAddress());
        d.setCenterLat(f.getCenterLat() == null ? null : f.getCenterLat().doubleValue());
        d.setCenterLng(f.getCenterLng() == null ? null : f.getCenterLng().doubleValue());
        d.setAreaHa(f.getAreaHa() == null ? null : f.getAreaHa().doubleValue());
        d.setBoundaryGeojson(f.getBoundaryGeojson());
        d.setDescription(f.getDescription());
        d.setStatus(FarmDto.StatusEnum.valueOf(f.getStatus().name()));
        d.setCreatedBy(f.getCreatedBy());
        d.setCreatedAt(OffsetDateTime.ofInstant(f.getCreatedAt(), ZoneOffset.UTC));
        d.setUpdatedAt(OffsetDateTime.ofInstant(f.getUpdatedAt(), ZoneOffset.UTC));
        return d;
    }
}