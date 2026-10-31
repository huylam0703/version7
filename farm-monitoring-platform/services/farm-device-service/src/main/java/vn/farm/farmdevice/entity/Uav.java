package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

@Entity
@Table(name = "Uavs",
        uniqueConstraints = @UniqueConstraint(name = "UQ_FD_Uavs_SerialNo", columnNames = "SerialNo"))
@Getter
@Setter
public class Uav extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "UavId")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FarmId", nullable = false)
    private Farm farm;

    @Column(name = "SerialNo", nullable = false, length = 50)
    private String serialNo;

    @Nationalized
    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(name = "Model", length = 100)
    private String model;

    @Column(name = "MaxFlightMin")
    private Short maxFlightMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 15)
    private UavStatus status = UavStatus.READY;
}
