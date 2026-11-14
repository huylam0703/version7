
package vn.farm.mission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<ApiResponse<MissionProgressResponse>> updateProgress(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionProgressRequest request
    ) {

        MissionProgressResponse result =
                missionProgressService.updateProgress(
                        missionId,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.<MissionProgressResponse>builder()
                        .code(1000)
                        .message("Mission progress updated successfully")
                        .result(result)
                        .build()
        );
    }


    @GetMapping("/{missionId}/progress")
    public ResponseEntity<ApiResponse<MissionProgressDetailResponse>> getProgress(
            @PathVariable UUID missionId
    ) {

        MissionProgressDetailResponse result =
                missionProgressService.getProgress(missionId);

        return ResponseEntity.ok(
                ApiResponse.<MissionProgressDetailResponse>builder()
                        .code(1000)
                        .message("Get mission progress successfully")
                        .result(result)
                        .build()
        );
    }

}
