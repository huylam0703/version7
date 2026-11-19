package vn.farm.mission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import vn.farm.core.api.ApiResponse;
import vn.farm.mission.dto.request.MissionHeartbeatRequest;
import vn.farm.mission.dto.response.MissionHeartbeatResponse;
import vn.farm.mission.service.MissionHeartbeatService;

import java.util.UUID;

@RestController
@RequestMapping("/missions")
@RequiredArgsConstructor
public class MissionHeartbeatController {

    private final MissionHeartbeatService heartbeatService;

    @PostMapping("/{missionId}/heartbeat")
    @PreAuthorize("hasAuthority('SCOPE_mission:heartbeat')")
    public ResponseEntity<ApiResponse<MissionHeartbeatResponse>> heartbeat(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionHeartbeatRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // 1. Validate JWT and client_id
        if (jwt == null) {
            throw new AccessDeniedException(
                    "UAV authentication is required"
            );
        }

        String clientId = jwt.getClaimAsString("client_id");

        if (clientId == null || clientId.isBlank()) {
            throw new AccessDeniedException(
                    "Missing UAV client_id"
            );
        }

        // 2. Process heartbeat
        MissionHeartbeatResponse result =
                heartbeatService.receiveHeartbeat(
                        missionId,
                        request,
                        clientId
                );

        // 3. Return response
        return ResponseEntity.ok(
                ApiResponse.<MissionHeartbeatResponse>builder()
                        .code(1000)
                        .message("Mission heartbeat received successfully")
                        .result(result)
                        .build()
        );
    }
}