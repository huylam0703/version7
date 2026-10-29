package vn.farm.farmdevice.event;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.farm.contracts.events.missionstatuschanged.MissionStatusChangedPayload;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.repository.GatewayRepository;
import vn.farm.farmdevice.repository.UavRepository;
import vn.farm.starter.events.FarmEventListener;

/**
 * mission.status_changed: RUNNING -> UAV IN_MISSION, gateway IN_USE;
 * COMPLETED/PARTIAL/FAILED/CANCELLED -> trả về READY. Không đè MAINTENANCE/RETIRED.
 * Starter đã bọc listener trong transaction và chống xử lý trùng theo eventId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionStatusListener {
    private final UavRepository uavs;
    private final GatewayRepository gateways;

    @FarmEventListener(event = "mission.status_changed")
    public void onMissionStatusChanged(MissionStatusChangedPayload p) {
        handle(String.valueOf(p.getNewStatus()), p.getUavId(), p.getGatewayId());
    }

    void handle(String newStatus, UUID uavId, UUID gatewayId) {
        switch (newStatus) {
            case "RUNNING" -> {
                switchUav(uavId, UavStatus.READY, UavStatus.IN_MISSION);
                switchGateway(gatewayId, GatewayStatus.READY, GatewayStatus.IN_USE);
            }
            case "COMPLETED", "PARTIAL", "FAILED", "CANCELLED" -> {
                switchUav(uavId, UavStatus.IN_MISSION, UavStatus.READY);
                switchGateway(gatewayId, GatewayStatus.IN_USE, GatewayStatus.READY);
            }
            default -> { /* DRAFT, PLANNED: chưa ảnh hưởng thiết bị */ }
        }
    }

    private void switchUav(UUID id, UavStatus from, UavStatus to) {
        if (id == null) return;
        uavs.findById(id).ifPresentOrElse(u -> {
            if (u.getStatus() == from) {
                u.setStatus(to);
                uavs.save(u);
            }
        }, () -> log.warn("mission.status_changed: không tìm thấy UAV {}", id));
    }

    private void switchGateway(UUID id, GatewayStatus from, GatewayStatus to) {
        if (id == null) return;
        gateways.findById(id).ifPresentOrElse(g -> {
            if (g.getStatus() == from) {
                g.setStatus(to);
                gateways.save(g);
            }
        }, () -> log.warn("mission.status_changed: không tìm thấy gateway {}", id));
    }
}
