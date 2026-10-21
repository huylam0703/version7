package vn.farm.mission.validation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.farm.mission.dto.request.CreateMissionRequest;
/** DEV ONLY: không xác minh các ID thuộc farm-device-service. Không dùng production. */
@Component @Profile("dev")
public class DevMissionReferenceValidator implements MissionReferenceValidator {
 @Override public void validate(CreateMissionRequest request) { }
}
