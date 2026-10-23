package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Bảng dbo_FD_Gateways chỉ có CreatedAt (không có UpdatedAt) nên không kế thừa AuditableEntity. */
@Entity
@Table(name = "dbo_FD_Gateways",
        uniqueConstraints = @UniqueConstraint(name = "UQ_FD_Gateways_GatewayUid", columnNames = "GatewayUid"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Gateway {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "GatewayId")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UavId")
    private Uav uav;

    @Column(name = "GatewayUid", nullable = false, length = 50)
    private String gatewayUid;

    @Nationalized
    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Column(name = "AuthKeyHash", nullable = false, length = 255)
    private String authKeyHash;

    @Nationalized
    @Column(name = "HwModel", length = 100)
    private String hwModel;

    @Column(name = "FirmwareVer", length = 20)
    private String firmwareVer;

    @Column(name = "StorageMb")
    private Integer storageMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 15)
    private GatewayStatus status = GatewayStatus.READY;

    @CreatedDate
    @Column(name = "CreatedAt", nullable = false, updatable = false, columnDefinition = "datetime2(0)")
    private Instant createdAt;
}
