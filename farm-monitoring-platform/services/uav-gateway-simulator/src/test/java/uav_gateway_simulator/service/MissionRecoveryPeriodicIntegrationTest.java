
package uav_gateway_simulator.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "simulator.recovery.initial-delay-ms=1000",
        "simulator.recovery.scan-interval-ms=1000"
})
class MissionRecoveryPeriodicIntegrationTest {

    @MockitoBean
    private MissionRecoveryCoordinator coordinator;

    @Test
    void shouldAutomaticallyScanRecoverableMissions() {
        // Startup invokes recovery once.
        // Periodic scheduler should invoke it again.
        verify(coordinator, timeout(7000).atLeast(2))
                .recoverAvailableMissions();
    }
}
