package vn.farm.farmdevice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.contracts.farmdevice.model.CreateFarmRequest;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.mapper.FarmMapper;
import vn.farm.farmdevice.repository.FarmRepository;
import vn.farm.starter.security.CurrentUser;

@Service
@RequiredArgsConstructor
public class FarmService {
    private final FarmRepository farms;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    @Transactional
    public Farm create(CreateFarmRequest request) {
        if (farms.existsByNameIgnoreCase(request.getName().trim()))
            throw BusinessException.conflict("FARM_NAME_EXISTS", "Tên nông trại đã tồn tại.");

        ensureValidJson(request.getBoundaryGeojson());       // tương đương CHECK ISJSON = 1

        Farm farm = FarmMapper.toEntity(request);
        farm.setCreatedBy(currentUser.id());
        return farms.save(farm);
    }

    private void ensureValidJson(String json) {
        if (json == null || json.isBlank()) return;
        try { objectMapper.readTree(json); }
        catch (Exception e) {
            throw BusinessException.invalid("INVALID_GEOJSON", "Ranh giới không phải JSON hợp lệ.");
        }
    }

}