
package uav_gateway_simulator.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "simulator.recovery.initial-delay-ms=3600000",
        "simulator.recovery.scan-interval-ms=3600000"
})
class MissionRecoveryStartupIntegrationTest {

    @MockitoBean
    private MissionRecoveryCoordinator coordinator;

    @Test
    void shouldTriggerRecoveryExactlyOnceOnStartup() {

        verify(coordinator, times(1))
                .recoverAvailableMissions();
    }
}
