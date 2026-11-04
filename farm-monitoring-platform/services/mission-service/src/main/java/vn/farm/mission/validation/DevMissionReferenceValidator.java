package vn.farm.mission.validation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.farm.mission.dto.request.CreateMissionRequest;
/** DEV ONLY: không xác minh các ID thuộc farm-device-service. Không dùng production. */
@Component
@ConditionalOnProperty(
        prefix = "mission.reference-validation",
        name = "mode",
        havingValue = "mock"
)
public class DevMissionReferenceValidator implements MissionReferenceValidator {
     @Override
     public void validate(CreateMissionRequest request) { }
}
