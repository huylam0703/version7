package vn.farm.farmdevice.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.farmdevice.dto.SensorDtos.TypeResponse;
import vn.farm.farmdevice.mapper.Mappers;
import vn.farm.farmdevice.repository.SensorTypeRepository;

@Service
@RequiredArgsConstructor
public class SensorTypeService {
    private final SensorTypeRepository types;

    @Transactional(readOnly = true)
    public List<TypeResponse> list() {
        return types.findAll(Sort.by("code")).stream().map(Mappers::toDto).toList();
    }
}
