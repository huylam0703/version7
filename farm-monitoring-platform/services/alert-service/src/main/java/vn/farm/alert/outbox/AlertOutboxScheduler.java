package vn.farm.alert.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.farm.alert.publisher.AlertEventPublisher;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "alert.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class AlertOutboxScheduler {
    private final AlertOutboxRepository repository;
    private final AlertEventPublisher publisher;

    @Scheduled(fixedDelayString = "${alert.outbox.poll-delay-ms:5000}")
    public void publishPendingEvents() {
        for (UUID eventId : repository.findDueIds(Instant.now(), PageRequest.of(0, 20))) {
            if (!publisher.publish(eventId)) {
                break;
            }
        }
    }
}
