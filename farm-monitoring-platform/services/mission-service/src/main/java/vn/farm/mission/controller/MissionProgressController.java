
package vn.farm.mission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.request.MissionProgressRequest;
import vn.farm.mission.dto.response.MissionProgressDetailResponse;
import vn.farm.mission.dto.response.MissionProgressResponse;
import vn.farm.mission.service.MissionProgressService;

import java.util.UUID;

@RestController
@RequestMapping("/missions")
@RequiredArgsConstructor
public class MissionProgressController {

    private final MissionProgressService missionProgressService;


    @PostMapping("/{missionId}/progress")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERATOR') or " +
                    "hasAuthority('SCOPE_mission:progress')"
    )
    public ResponseEntity<ApiResponse<MissionProgressResponse>> updateProgress(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionProgressRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean isOperator = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_OPERATOR")
                );

        MissionProgressResponse result;

        if (isOperator) {
            result = missionProgressService.updateProgress(
                    missionId,
                    request
            );
        } else {
            String clientId = jwt != null
                    ? jwt.getClaimAsString("client_id")
                    : null;

            result = missionProgressService.updateProgress(
                    missionId,
                    request,
                    clientId
            );
        }

        return ResponseEntity.ok(
                ApiResponse.<MissionProgressResponse>builder()
                        .code(1000)
                        .message("Mission progress updated successfully")
                        .result(result)
                        .build()
        );
    }




    @GetMapping("/{missionId}/progress")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'OPERATOR') or " +
                    "hasAuthority('SCOPE_mission:read')"
    )
    public ResponseEntity<ApiResponse<MissionProgressDetailResponse>> getProgress(
            @PathVariable UUID missionId,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean isOperator = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN")
                                || authority.getAuthority().equals("ROLE_OPERATOR")
                );

        MissionProgressDetailResponse result;

        if (isOperator) {
            result = missionProgressService.getProgress(missionId);
        } else {
            String clientId = jwt != null
                    ? jwt.getClaimAsString("client_id")
                    : null;

            result = missionProgressService.getProgress(
                    missionId,
                    clientId
            );
        }

        return ResponseEntity.ok(
                ApiResponse.<MissionProgressDetailResponse>builder()
                        .code(1000)
                        .message("Get mission progress successfully")
                        .result(result)
                        .build()
        );
    }


}
