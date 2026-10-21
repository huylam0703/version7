package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="dbo_M_Missions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Mission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="MissionId",columnDefinition="uniqueidentifier")
    UUID missionId;

    @Column(name="FarmId",nullable=false)
    UUID farmId;

    @Column(name="UavId",nullable=false)
    UUID uavId;

    @Column(name="GatewayId",nullable=false)
    UUID gatewayId;

    @Column(name="CreatedBy",nullable=false)
    UUID createdBy;

    @Nationalized
    @Column(name="Name",nullable=false,length=150)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(name="Status",nullable=false,length=15)
    MissionStatus status;

    @Column(name="PlannedAt",columnDefinition="datetime2(0)")
    Instant plannedAt;

    @Column(name="StartedAt",columnDefinition="datetime2(3)")
    Instant startedAt;

    @Column(name="EndedAt",columnDefinition="datetime2(3)")
    Instant endedAt;

    @Column(name="TotalNodes",nullable=false)
    int totalNodes;

    @Column(name="SuccessNodes",nullable=false)
    int successNodes;

    @Column(name="FailedNodes",nullable=false)
    int failedNodes;

    @Nationalized
    @Column(name="Notes",columnDefinition="nvarchar(max)")
    String notes;
}
