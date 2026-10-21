package vn.farm.mission.validation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vn.farm.mission.dto.request.CreateMissionRequest;
/** Fail-closed until farm-device internal API + service token contract is agreed. */
@Component @Profile("!dev")
public class StrictMissionReferenceValidator implements MissionReferenceValidator {
 @Override public void validate(CreateMissionRequest request) {
  throw new IllegalStateException("Mission reference validation chưa được tích hợp: cần farm-device internal API");
 }
}
