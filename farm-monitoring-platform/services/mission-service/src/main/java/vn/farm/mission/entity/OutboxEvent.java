package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "dbo_M_OutboxEvents",
        indexes = {
                @Index(
                        name = "IX_Outbox_Status_NextRetry",
                        columnList = "Status,NextRetryAt"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent {

    @Id
    @Column(name = "EventId", columnDefinition = "uniqueidentifier")
    UUID eventId;

    @Column(name = "EventType", nullable = false, length = 100)
    String eventType;

    @Column(name = "Payload", nullable = false, columnDefinition = "nvarchar(max)")
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    OutboxStatus status;

    @Builder.Default
    @Column(name = "Attempts", nullable = false)
    int attempts = 0;

    @Column(name = "NextRetryAt", nullable = false, columnDefinition = "datetime2(3)")
    Instant nextRetryAt;

    @Column(name = "PublishedAt", columnDefinition = "datetime2(3)")
    Instant publishedAt;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime2(3)")
    Instant createdAt;

    @Version
    @Column(name = "Version")
    Long version;

    @Column(name = "LockedUntil", columnDefinition = "datetime2(3)")
    Instant lockedUntil;

    @Column(name = "LockToken", columnDefinition = "uniqueidentifier")
    UUID lockToken;
}