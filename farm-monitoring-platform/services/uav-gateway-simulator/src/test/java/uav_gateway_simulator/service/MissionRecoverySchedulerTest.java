
package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class MissionRecoverySchedulerTest {

    private MissionRecoveryCoordinator coordinator;
    private MissionRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        coordinator = mock(MissionRecoveryCoordinator.class);
        scheduler = new MissionRecoveryScheduler(coordinator);
    }

    @Test
    void shouldRecoverOnStartup() {
        scheduler.onStartup();

        verify(coordinator, times(1))
                .recoverAvailableMissions();
    }

    @Test
    void shouldRecoverOnScheduledScan() {
        scheduler.scheduledScan();

        verify(coordinator, times(1))
                .recoverAvailableMissions();
    }

    @Test
    void shouldHandleRecoveryScanFailure() {
        doThrow(new RuntimeException("Mission Service unavailable"))
                .when(coordinator)
                .recoverAvailableMissions();

        scheduler.scheduledScan();

        verify(coordinator, times(1))
                .recoverAvailableMissions();
    }

    @Test
    void shouldAllowNextScanAfterFailure() {
        doThrow(new RuntimeException("Temporary failure"))
                .doNothing()
                .when(coordinator)
                .recoverAvailableMissions();

        scheduler.scheduledScan();
        scheduler.scheduledScan();

        verify(coordinator, times(2))
                .recoverAvailableMissions();
    }
}
