package vn.farm.mission.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.mission.entity.OutboxEvent;
import vn.farm.mission.entity.OutboxStatus;
import vn.farm.mission.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    @Transactional
    public List<UUID> claimPendingEvents() {

        Instant now = Instant.now();

        List<OutboxEvent> events = repository
                .findByStatusAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        now,
                        PageRequest.of(0, 20)
                );

        for (OutboxEvent event : events) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedUntil(now.plusSeconds(60));
            event.setLockToken(UUID.randomUUID());
        }

        repository.saveAllAndFlush(events);

        return events.stream()
                .map(OutboxEvent::getEventId)
                .toList();
    }
}