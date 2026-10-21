package vn.farm.mission.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component("missionAccess")
public class MissionAccess {
 @Value("${farm.security.enabled:true}") private boolean securityEnabled;
 public boolean canCreateInDev() { return !securityEnabled; }
}
