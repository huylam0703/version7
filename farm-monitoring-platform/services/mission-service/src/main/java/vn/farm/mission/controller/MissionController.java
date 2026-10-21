package vn.farm.mission.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.request.CreateMissionRequest;
import vn.farm.mission.dto.response.MissionResponse;
import vn.farm.mission.service.MissionService;

@RestController
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/missions")
public class MissionController {
    MissionService missionService;

    @PostMapping("/createMission")
    @PreAuthorize("hasRole('OPERATOR') or @missionAccess.canCreateInDev()")
    public ResponseEntity<ApiResponse<MissionResponse>> createMission(@Valid @RequestBody CreateMissionRequest request) {
        MissionResponse result = missionService.createMission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Mission created successfully")
                        .result(result)
                        .build());
    }
}
