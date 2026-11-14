package vn.farm.mission.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxClaimService claimService;
    private final OutboxPublisher publisher;
    private final OutboxRecoveryService recoveryService;

    @Scheduled(fixedDelay = 5000)
    public void processOutbox() {

        log.info("Outbox scheduler is running...");

        recoveryService.recoverExpiredClaims();

        for (UUID eventId : claimService.claimPendingEvents()) {
            publisher.publish(eventId);
        }
    }
}