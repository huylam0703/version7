package vn.farm.alert.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.util.UUID;

@Entity
@Table(name = "dbo_AL_Alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID nodeId;

    private UUID missionId;

    @Column(length = 50, nullable = false)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Nationalized
    @Column(length = 500, nullable = false)
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status = AlertStatus.NEW;

    // Task 8: ID bản ghi cảm biến đã tạo ra Alert
    @Column(name = "readingId")
    private UUID readingId;
}
