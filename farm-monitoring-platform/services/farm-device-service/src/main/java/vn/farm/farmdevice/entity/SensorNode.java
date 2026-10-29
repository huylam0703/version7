package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

@Entity
@Table(name = "dbo_FD_SensorNodes",
        uniqueConstraints = @UniqueConstraint(name = "UQ_FD_SensorNodes_DeviceUid", columnNames = "DeviceUid"),
        indexes = @Index(name = "IX_FD_SensorNodes_ZoneId", columnList = "ZoneId"))
@Getter
@Setter
public class SensorNode extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "NodeId")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ZoneId", nullable = false)
    private Zone zone;

    @Column(name = "DeviceUid", nullable = false, length = 50)
    private String deviceUid;

    @Nationalized
    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "AuthKeyHash", nullable = false, length = 255)
    private String authKeyHash;

    @Column(name = "Lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "Lng", precision = 9, scale = 6)
    private BigDecimal lng;

    @Column(name = "FirmwareVer", length = 20)
    private String firmwareVer;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 15)
    private NodeStatus status = NodeStatus.ACTIVE;

    @Column(name = "InstalledAt", columnDefinition = "date")
    private LocalDate installedAt;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<NodeChannel> channels = new ArrayList<>();
}
