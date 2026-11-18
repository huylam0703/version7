package vn.farm.alert.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.farm.alert.dto.request.CreateAlertRequest;
import vn.farm.alert.dto.response.AlertResponse;
import vn.farm.alert.entity.Alert;

@Mapper(componentModel = "spring")
public interface AlertMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    Alert toAlert(CreateAlertRequest request);

    AlertResponse toAlertResponse(Alert alert);
}
