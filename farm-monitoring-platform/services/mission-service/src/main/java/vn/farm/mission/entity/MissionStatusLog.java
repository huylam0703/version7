package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="dbo_M_MissionStatusLogs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionStatusLog {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="LogId")
    UUID logId;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="MissionId",nullable=false)
    Mission mission;

    @Column(name="ChangedBy")
    UUID changedBy;

    @Column(name="Source",nullable=false,length=10)
    String source;

    @Column(name="OldStatus",length=20)
    String oldStatus;

    @Column(name="NewStatus",nullable=false,length=20)
    String newStatus;

    @Nationalized
    @Column(name="Note",length=500)
    String note;

    @Column(name="CreatedAt",nullable=false,columnDefinition="datetime2(3)")
    Instant createdAt;
}
