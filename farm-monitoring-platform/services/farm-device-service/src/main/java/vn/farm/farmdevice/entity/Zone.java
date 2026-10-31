package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

@Entity
@Table(name = "Zones",
        uniqueConstraints = @UniqueConstraint(name = "UQ_FD_Zones_Farm_Code", columnNames = {"FarmId", "Code"}))
@Getter
@Setter
public class Zone extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ZoneId")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FarmId", nullable = false)
    private Farm farm;

    @Column(name = "Code", nullable = false, length = 30)
    private String code;

    @Nationalized
    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(name = "CropType", length = 100)
    private String cropType;

    @Column(name = "CenterLat", precision = 9, scale = 6)
    private BigDecimal centerLat;

    @Column(name = "CenterLng", precision = 9, scale = 6)
    private BigDecimal centerLng;

    @Nationalized
    @Column(name = "BoundaryGeojson", columnDefinition = "nvarchar(max)")
    private String boundaryGeojson;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 10)
    private FarmStatus status = FarmStatus.ACTIVE;
}
