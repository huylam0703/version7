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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.request.*;
import vn.farm.mission.dto.response.*;
import vn.farm.mission.entity.MissionStatus;
import vn.farm.mission.service.MissionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/missions")
public class MissionController {
    MissionService missionService;

    @PostMapping("/createMission")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<MissionResponse>> createMission(@Valid @RequestBody CreateMissionRequest request) {
        MissionResponse result = missionService.createMission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Mission created successfully")
                        .result(result)
                        .build());
    }


    @GetMapping("/{missionId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERATOR') or " +
                    "hasAuthority('SCOPE_mission:read')"
    )
    public ResponseEntity<ApiResponse<MissionDetailResponse>> getMissionById(
            @PathVariable UUID missionId,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean isOperator = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_OPERATOR")
                );

        MissionDetailResponse result;

        if (isOperator) {
            // ADMIN / OPERATOR
            result = missionService.getMissionById(missionId);
        } else {
            // UAV Simulator
            String clientId = jwt != null
                    ? jwt.getClaimAsString("client_id")
                    : null;

            result = missionService.getMissionById(
                    missionId,
                    clientId
            );
        }

        return ResponseEntity.ok(
                ApiResponse.<MissionDetailResponse>builder()
                        .code(1000)
                        .message("Get mission successfully")
                        .result(result)
                        .build()
        );
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
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
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERATOR') or " +
                    "hasAuthority('SCOPE_mission:results')"
    )
    public ResponseEntity<ApiResponse<MissionResponse>> receiveMissionResults(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionResultRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean isOperator = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_OPERATOR")
                );

        MissionResponse result;

        if (isOperator) {
            // ADMIN / OPERATOR
            result = missionService.receiveMissionResults(
                    missionId,
                    request
            );
        } else {
            // UAV Simulator
            String clientId = jwt != null
                    ? jwt.getClaimAsString("client_id")
                    : null;

            result = missionService.receiveMissionResults(
                    missionId,
                    request,
                    clientId
            );
        }

        return ResponseEntity.ok(
                ApiResponse.<MissionResponse>builder()
                        .message("Receive mission results successfully")
                        .result(result)
                        .build()
        );
    }

    @PostMapping("/{missionId}/plan")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<MissionResponse>> planMission(
            @PathVariable UUID missionId
    ) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<MissionResponse>builder()
                        .message("Plan mission successfully")
                        .result(missionService.planMission(missionId))
                        .build());
    }

    @GetMapping("/{missionId}/results")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<List<MissionNodeResultResponse>>> getMissionResults(
            @PathVariable UUID missionId) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<List<MissionNodeResultResponse>>builder()
                        .message("Get mission results successfully")
                        .result(missionService.getMissionResults(missionId))
                        .build());
    }

    @GetMapping("/{missionId}/status-logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<MissionStatusLogResponse>> getMissionStatusHistory(
            @PathVariable UUID missionId) {

        return ApiResponse.<List<MissionStatusLogResponse>>builder()
                .code(1000)
                .message("Get mission status history successfully")
                .result(missionService.getMissionStatusHistory(missionId))
                .build();
    }

    @PostMapping("/{missionId}/ack")
    @PreAuthorize("hasAuthority('SCOPE_mission:ack')")
    public ApiResponse<MissionAckResponse> ackMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionAckRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String clientId = jwt.getClaimAsString("client_id");

        MissionAckResponse response = missionService.ackMission(
                missionId,
                request,
                clientId
        );

        return ApiResponse.<MissionAckResponse>builder()
                .message("Mission acknowledged successfully")
                .result(response)
                .build();
    }

    @GetMapping("/recoverable")
    @PreAuthorize("hasAuthority('SCOPE_mission:read')")
    public ApiResponse<List<RecoverableMissionResponse>> getRecoverableMissions(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String clientId = jwt.getClaimAsString("client_id");

        List<RecoverableMissionResponse> result =
                missionService.getRecoverableMissions(clientId);

        return ApiResponse.<List<RecoverableMissionResponse>>builder()
                .code(1000)
                .message("Get recoverable missions successfully")
                .result(result)
                .build();
    }

    @PostMapping("/{missionId}/recovery-claim")
    @PreAuthorize("hasAuthority('SCOPE_mission:read')")
    public ApiResponse<MissionRecoveryClaimResponse> claimRecovery(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionRecoveryClaimRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String clientId = jwt.getClaimAsString("client_id");

        return ApiResponse.<MissionRecoveryClaimResponse>builder()
                .code(1000)
                .message("Mission recovery claimed successfully")
                .result(missionService.claimRecovery(
                        missionId,
                        request,
                        clientId
                ))
                .build();
    }

    @PostMapping("/{missionId}/recovery-renew")
    @PreAuthorize("hasAuthority('SCOPE_mission:read')")
    public ApiResponse<MissionRecoveryClaimResponse> renewRecoveryLease(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionRecoveryRenewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ApiResponse.<MissionRecoveryClaimResponse>builder()
                .code(1000)
                .message("Recovery lease renewed successfully")
                .result(missionService.renewRecoveryLease(
                        missionId,
                        request,
                        jwt.getClaimAsString("client_id")
                ))
                .build();
    }
}
