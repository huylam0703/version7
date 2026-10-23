package vn.farm.mission.controller;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.request.CreateMissionRequest;
import vn.farm.mission.dto.request.MissionResultRequest;
import vn.farm.mission.dto.request.UpdateMissionRequest;
import vn.farm.mission.dto.response.MissionDetailResponse;
import vn.farm.mission.dto.response.MissionResponse;
import vn.farm.mission.entity.MissionStatus;
import vn.farm.mission.service.MissionService;

import java.util.UUID;

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

    @GetMapping("/{missionId}")
    public ResponseEntity<ApiResponse<MissionDetailResponse>> getMissionById(
            @PathVariable UUID missionId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionDetailResponse>builder()
                        .message("Get mission successfully")
                        .result(missionService.getMissionById(missionId))
                        .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<MissionResponse>>> getAllMissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) MissionStatus status
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<Page<MissionResponse>>builder()
                        .message("Get missions successfully")
                        .result(missionService.getAllMissions(page, size, status))
                        .build());
    }

    @PutMapping("/{missionId}")
    public ResponseEntity<ApiResponse<MissionResponse>> updateMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody UpdateMissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Update mission successfully")
                        .result(missionService.updateMission(missionId, request))
                        .build());
    }

    @PostMapping("/{missionId}/start")
    public ResponseEntity<ApiResponse<MissionResponse>> startMission(
            @PathVariable UUID missionId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Start mission successfully")
                        .result(missionService.startMission(missionId))
                        .build());
    }

    @PostMapping("/{missionId}/cancel")
    public ResponseEntity<ApiResponse<MissionResponse>> cancelMission(
            @PathVariable UUID missionId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Cancel mission successfully")
                        .result(missionService.cancelMission(missionId))
                        .build());
    }

    @PostMapping("/{missionId}/results")
    public ResponseEntity<ApiResponse<MissionResponse>> receiveMissionResults(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionResultRequest request
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Receive mission results successfully")
                        .result(missionService.receiveMissionResults(missionId, request))
                        .build());
    }
}
