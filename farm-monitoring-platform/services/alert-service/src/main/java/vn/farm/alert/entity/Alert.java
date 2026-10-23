
package vn.farm.alert.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import vn.farm.starter.jpa.AuditableEntity;

import java.util.UUID;

@Entity
@Table(name = "dbo_AL_Alerts")
public class Alert extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "AlertId")
    private UUID id;

    @Column(name = "NodeId", nullable = false)
    private UUID nodeId;

    @Column(name = "AlertType", nullable = false, length = 50)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "Severity", nullable = false, length = 20)
    private AlertSeverity severity;

    @Nationalized
    @Column(name = "Message", nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private AlertStatus status = AlertStatus.NEW;

    // Constructor
    public Alert() {
    }

    // ID
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    // Node ID
    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    // Alert Type
    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    // Severity
    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    // Message
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    // Status
    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }
}
