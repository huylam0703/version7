package vn.farm.mission.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.farm.mission.entity.MissionExecution;
import vn.farm.mission.entity.MissionExecutionStatus;
import vn.farm.mission.repository.MissionExecutionRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionAckRetryScheduler {

    private final MissionExecutionRepository executionRepository;
    private final MissionAckRetryService retryService;

    @Scheduled(fixedDelay = 5000)
    public void processPendingAcks() {

        List<MissionExecution> pendingExecutions =
                executionRepository.findByStatus(
                        MissionExecutionStatus.WAITING_ACK
                );

        for (MissionExecution execution : pendingExecutions) {
            try {
                retryService.processExecution(
                        execution.getExecutionId()
                );
            } catch (Exception e) {
                log.error(
                        "ACK retry processing failed: executionId={}",
                        execution.getExecutionId(),
                        e
                );
            }
        }
    }
}