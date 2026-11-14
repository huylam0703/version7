package vn.farm.mission.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.farm.mission.entity.OutboxEvent;
import vn.farm.mission.entity.OutboxStatus;
import vn.farm.mission.repository.OutboxEventRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxStateService {

    private final OutboxEventRepository repository;

    private static final int MAX_ATTEMPTS = 5;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId, UUID lockToken) {
        OutboxEvent event = findOwnedEvent(eventId, lockToken);

        if (event == null) {
            return;
        }

        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        event.setLockedUntil(null);
        event.setLockToken(null);

        repository.save(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedOrRetry(UUID eventId, UUID lockToken) {
        OutboxEvent event = findOwnedEvent(eventId, lockToken);

        if (event == null) {
            return;
        }

        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);

        if (attempts >= MAX_ATTEMPTS) {
            event.setStatus(OutboxStatus.FAILED);
        } else {
            event.setStatus(OutboxStatus.PENDING);

            long delaySeconds = Math.min(
                    5L * (1L << (attempts - 1)),
                    60L
            );

            event.setNextRetryAt(
                    Instant.now().plusSeconds(delaySeconds)
            );
        }

        event.setLockedUntil(null);
        event.setLockToken(null);

        repository.save(event);
    }

    private OutboxEvent findOwnedEvent(
            UUID eventId,
            UUID lockToken
    ) {
        OutboxEvent event = repository.findById(eventId)
                .orElse(null);

        if (event == null
                || event.getStatus() != OutboxStatus.PROCESSING
                || !lockToken.equals(event.getLockToken())) {
            return null;
        }

        return event;
    }
}