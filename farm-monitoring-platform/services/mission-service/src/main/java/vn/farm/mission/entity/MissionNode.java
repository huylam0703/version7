package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="dbo_M_MissionNodes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MissionNode {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="MissionNodeId")
    UUID missionNodeId;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="MissionId",nullable=false)
    Mission mission;

    @Column(name="NodeId",nullable=false)
    UUID nodeId; // logical ref: farm-device-service

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name="WaypointId")
    Waypoint waypoint;

    @Column(name="Result",nullable=false,length=10)
    String result="PENDING";

    @Column(name="ErrorCode",length=30)
    String errorCode;

    @Nationalized
    @Column(name="ErrorMessage",length=255)
    String errorMessage;

    @Column(name="RecordsCount",nullable=false)
    int recordsCount=0;

    @Column(name="Attempts",nullable=false)
    Integer attempts=0;

    @Column(name="CollectedAt",columnDefinition="datetime2(3)")
    Instant collectedAt;
}
