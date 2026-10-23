package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dbo_FD_NodeChannels",
        uniqueConstraints = @UniqueConstraint(name = "UQ_FD_NodeChannels_Node_Type", columnNames = {"NodeId", "TypeId"}))
@Getter
@Setter
public class NodeChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ChannelId")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "NodeId", nullable = false)
    private SensorNode node;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "TypeId", nullable = false)
    private SensorType type;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "ChannelNo", nullable = false)
    private Short channelNo;

    @Column(name = "CalibrationOffset", nullable = false)
    private Double calibrationOffset = 0.0;

    @Column(name = "IsActive", nullable = false)
    private boolean active = true;
}
