package vn.farm.farmdevice.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.farm.farmdevice.entity.Gateway;
import vn.farm.farmdevice.entity.GatewayStatus;
import vn.farm.farmdevice.entity.Uav;
import vn.farm.farmdevice.entity.UavStatus;
import vn.farm.farmdevice.repository.GatewayRepository;
import vn.farm.farmdevice.repository.UavRepository;

class MissionStatusListenerTest {
    private final UavRepository uavs = mock(UavRepository.class);
    private final GatewayRepository gateways = mock(GatewayRepository.class);
    private final MissionStatusListener listener = new MissionStatusListener(uavs, gateways);
    private final UUID uavId = UUID.randomUUID();
    private final UUID gwId = UUID.randomUUID();
    private Uav uav;
    private Gateway gw;

    @BeforeEach
    void setUp() {
        uav = new Uav();
        gw = new Gateway();
        when(uavs.findById(uavId)).thenReturn(Optional.of(uav));
        when(gateways.findById(gwId)).thenReturn(Optional.of(gw));
    }

    @Test
    void runningLocksDevices() {
        listener.handle("RUNNING", uavId, gwId);
        assertThat(uav.getStatus()).isEqualTo(UavStatus.IN_MISSION);
        assertThat(gw.getStatus()).isEqualTo(GatewayStatus.IN_USE);
    }

    @Test
    void finishedReleasesDevices() {
        uav.setStatus(UavStatus.IN_MISSION);
        gw.setStatus(GatewayStatus.IN_USE);
        listener.handle("FAILED", uavId, gwId);
        assertThat(uav.getStatus()).isEqualTo(UavStatus.READY);
        assertThat(gw.getStatus()).isEqualTo(GatewayStatus.READY);
    }

    @Test
    void doesNotOverrideMaintenance() {
        uav.setStatus(UavStatus.MAINTENANCE);
        listener.handle("RUNNING", uavId, gwId);
        listener.handle("COMPLETED", uavId, gwId);
        assertThat(uav.getStatus()).isEqualTo(UavStatus.MAINTENANCE);
    }

    @Test
    void draftAndPlannedDoNothing() {
        listener.handle("DRAFT", uavId, gwId);
        listener.handle("PLANNED", uavId, gwId);
        assertThat(uav.getStatus()).isEqualTo(UavStatus.READY);
    }
}
