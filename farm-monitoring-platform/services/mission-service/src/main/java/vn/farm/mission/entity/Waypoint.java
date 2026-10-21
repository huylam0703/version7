package vn.farm.mission.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="dbo_M_Waypoints")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Waypoint {
    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    @Column(name="WaypointId")
    UUID waypointId;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="MissionId",nullable=false)
    Mission mission;

    @Column(name="Seq",nullable=false)
    short seq;

    @Column(name="Lat",precision=9,scale=6,nullable=false)
    BigDecimal lat;

    @Column(name="Lng",precision=9,scale=6,nullable=false)
    BigDecimal lng;

    @Column(name="AltM",precision=6,scale=2,nullable=false)
    BigDecimal altM=BigDecimal.ZERO;

    @Column(name="HoverSec",nullable=false)
    short hoverSec=0;

    @Column(name="Status",nullable=false,length=10)
    String status="PENDING";

    @Column(name="ReachedAt",columnDefinition="datetime2(3)")
    Instant reachedAt;
}
