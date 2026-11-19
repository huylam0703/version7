package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionRecoveryScheduler {

    private final MissionRecoveryCoordinator recoveryCoordinator;

    // Tự Recovery sau khi Simulator khởi động thành công.
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Starting automatic mission recovery scan");
        scan();
    }

    // Quét lại mỗi 30 giây.
    @Scheduled(
            initialDelayString =
                    "${simulator.recovery.initial-delay-ms:30000}",
            fixedDelayString =
                    "${simulator.recovery.scan-interval-ms:30000}"
    )
    public void scheduledScan() {
        scan();
    }

    private void scan() {
        try {
            recoveryCoordinator.recoverAvailableMissions();
        } catch (Exception e) {
            log.error("Automatic mission recovery scan failed", e);
        }
    }
}