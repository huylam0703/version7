package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

@Entity
@Table(name = "dbo_FD_Farms")
@Getter
@Setter
public class Farm extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "FarmId")
    private UUID id;

    @Nationalized                                   // NVARCHAR
    @Column(name = "Name", nullable = false, length = 150)
    private String name;

    @Nationalized
    @Column(name = "Address", length = 255)
    private String address;

    @Column(name = "CenterLat", precision = 9, scale = 6)
    private BigDecimal centerLat;

    @Column(name = "CenterLng", precision = 9, scale = 6)
    private BigDecimal centerLng;

    @Column(name = "AreaHa", precision = 10, scale = 2)
    private BigDecimal areaHa;

    @Nationalized
    @Column(name = "BoundaryGeojson", columnDefinition = "nvarchar(max)")
    private String boundaryGeojson;

    @Nationalized
    @Column(name = "Description", columnDefinition = "nvarchar(max)")
    private String description;

    @Enumerated(EnumType.STRING)                    // VARCHAR('ACTIVE'/'INACTIVE')
    @Column(name = "Status", nullable = false, length = 10)
    private FarmStatus status = FarmStatus.ACTIVE;

    @Column(name = "CreatedBy", nullable = false, updatable = false)
    private UUID createdBy;                         // ref sang user-auth, không có FK
}