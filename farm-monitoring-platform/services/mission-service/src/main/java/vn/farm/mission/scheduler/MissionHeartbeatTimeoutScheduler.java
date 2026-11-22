
package vn.farm.mission.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.farm.mission.entity.MissionExecutionStatus;
import vn.farm.mission.entity.MissionStatus;
import vn.farm.mission.repository.MissionExecutionRepository;
import vn.farm.mission.service.MissionService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionHeartbeatTimeoutScheduler {

    private final MissionExecutionRepository executionRepository;
    private final MissionService missionService;

    @Scheduled(fixedDelay = 5000)
    public void processHeartbeatTimeouts() {

        Instant cutoff = Instant.now().minusSeconds(150);

        List<UUID> candidates =
                executionRepository.findHeartbeatTimeoutCandidates(
                        MissionExecutionStatus.EXECUTING,
                        MissionStatus.RUNNING,
                        cutoff
                );

        for (UUID executionId : candidates) {
            try {
                missionService.failMissionOnHeartbeatTimeout(
                        executionId
                );
            } catch (Exception ex) {
                log.error(
                        "Failed to process heartbeat timeout: executionId={}",
                        executionId,
                        ex
                );
            }
        }
    }
}
