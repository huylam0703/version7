
package uav_gateway_simulator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uav_gateway_simulator.dto.MissionReadyPayload;
import uav_gateway_simulator.service.MissionProcessingService;

@RestController
@RequestMapping("/internal/missions")
@RequiredArgsConstructor
public class MissionRecoveryController {

    private final MissionProcessingService missionProcessingService;

    @PostMapping("/recover")
    public ResponseEntity<String> recover(
            @RequestBody MissionReadyPayload payload
    ) {
        return ResponseEntity.status(503)
                .body("Mission recovery API is temporarily disabled");
    }
}
