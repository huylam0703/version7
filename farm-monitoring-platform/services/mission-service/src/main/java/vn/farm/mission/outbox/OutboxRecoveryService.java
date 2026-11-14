package vn.farm.mission.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.mission.entity.OutboxEvent;
import vn.farm.mission.entity.OutboxStatus;
import vn.farm.mission.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRecoveryService {

    private final OutboxEventRepository repository;

    @Transactional
    public void recoverExpiredClaims() {

        List<OutboxEvent> events =
                repository.findByStatusAndLockedUntilBefore(
                        OutboxStatus.PROCESSING,
                        Instant.now()
                );

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PENDING);
            event.setLockToken(null);
            event.setLockedUntil(null);
            event.setNextRetryAt(Instant.now());
        }

        repository.saveAll(events);
    }
}