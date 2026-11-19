package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "dbo_M_MissionExecutions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_MissionExecution_MissionId",
                        columnNames = "MissionId"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ExecutionId", columnDefinition = "uniqueidentifier")
    UUID executionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MissionId", nullable = false)
    Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    MissionExecutionStatus status;

    @Column(name = "AssignedClientId", length = 150)
    String assignedClientId;

    @Column(name = "StartedAt", columnDefinition = "datetime2(3)")
    Instant startedAt;

    @Column(name = "AckDeadline", columnDefinition = "datetime2(3)")
    Instant ackDeadline;

    @Column(name = "AcknowledgedAt", columnDefinition = "datetime2(3)")
    Instant acknowledgedAt;

    @Builder.Default
    @Column(name = "NotificationAttempts", nullable = false)
    int notificationAttempts = 0;

    @Column(name = "LastHeartbeatAt", columnDefinition = "datetime2(3)")
    Instant lastHeartbeatAt;

    @Builder.Default
    @Column(name = "LastConfirmedSequence", nullable = false)
    long lastConfirmedSequence = 0L;

    @Version
    @Column(name = "Version")
    Long version;

    @Column(name = "RecoveryOwnerId", length = 100)
    String recoveryOwnerId;

    @Column(name = "RecoveryLeaseUntil", columnDefinition = "datetime2(3)")
    Instant recoveryLeaseUntil;

    @Column(name = "RecoveryGeneration", nullable = false)
    @Builder.Default
    long recoveryGeneration = 0L;
}
