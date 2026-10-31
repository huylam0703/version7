package vn.farm.farmdevice.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "SensorTypes")
@Getter
@Setter
public class SensorType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "TypeId")
    private UUID id;

    @Column(name = "Code", nullable = false, length = 30, unique = true)
    private String code;

    @Nationalized
    @Column(name = "Name", nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(name = "Unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "MinValid")
    private Double minValid;

    @Column(name = "MaxValid")
    private Double maxValid;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "Decimals", nullable = false)
    private Short decimals = 1;
}
