
package vn.farm.mission.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.response.MissionExecutionProgressResponse;
import vn.farm.mission.service.MissionService;

import java.util.UUID;

@RestController
@RequestMapping("/missions")
@RequiredArgsConstructor
public class MissionExecutionProgressController {

    private final MissionService missionService;

    @GetMapping("/{missionId}/executions/{executionId}/progress")
    @PreAuthorize(
            "hasAuthority('SCOPE_mission:read')"
    )
    public ResponseEntity<ApiResponse<MissionExecutionProgressResponse>>
    getExecutionProgress(
            @PathVariable UUID missionId,
            @PathVariable UUID executionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String clientId = jwt != null
                ? jwt.getClaimAsString("client_id")
                : null;

        MissionExecutionProgressResponse result =
                missionService.getExecutionProgress(
                        missionId,
                        executionId,
                        clientId
                );

        return ResponseEntity.ok(
                ApiResponse.<MissionExecutionProgressResponse>builder()
                        .code(1000)
                        .message("Get mission execution progress successfully")
                        .result(result)
                        .build()
        );
    }
}
