package vn.farm.alert.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dbo_AL_OutboxEvents", indexes =
        @Index(name = "IX_AlertOutbox_Pending", columnList = "PublishedAt,NextRetryAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertOutboxEvent {
    @Id
    @Column(name = "EventId")
    private UUID eventId;

    @Column(name = "Payload", nullable = false, length = Length.LONG32)
    private String payload;

    @Column(name = "Attempts", nullable = false)
    private long attempts;

    @Column(name = "NextRetryAt", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "PublishedAt")
    private Instant publishedAt;

    AlertOutboxEvent(UUID eventId, String payload, Instant now) {
        this.eventId = eventId;
        this.payload = payload;
        this.nextRetryAt = now;
    }

    public void markPublished() {
        publishedAt = Instant.now();
    }

    public void scheduleRetry() {
        attempts++;
        long delaySeconds = Math.min(5L << Math.min(attempts - 1, 4), 60L);
        nextRetryAt = Instant.now().plusSeconds(delaySeconds);
    }
}
