package vn.farm.farmdevice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.farm.core.BusinessException;
import vn.farm.farmdevice.dto.SensorDtos.NodeConnectionResponse;
import vn.farm.farmdevice.dto.SensorDtos.UpdateNodeRequest;
import vn.farm.farmdevice.entity.ConnectionType;
import vn.farm.farmdevice.entity.Farm;
import vn.farm.farmdevice.entity.NodeStatus;
import vn.farm.farmdevice.entity.SensorNode;
import vn.farm.farmdevice.entity.Zone;
import vn.farm.farmdevice.repository.SensorNodeRepository;
import vn.farm.farmdevice.repository.SensorTypeRepository;
import vn.farm.farmdevice.repository.ZoneRepository;

class SensorNodeConnectionTest {
    private final SensorNodeRepository nodes = mock(SensorNodeRepository.class);
    private final SensorNodeService service = new SensorNodeService(nodes, mock(SensorTypeRepository.class),
            mock(ZoneRepository.class), mock(PasswordEncoder.class));
    private final UUID id = UUID.randomUUID();
    private SensorNode node;

    @BeforeEach
    void setUp() {
        node = new SensorNode();
        node.setDeviceUid("ESP32-AABB");
        node.setZone(new Zone());
        node.getZone().setFarm(new Farm());
        when(nodes.findById(id)).thenReturn(Optional.of(node));
        when(nodes.save(any(SensorNode.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static UpdateNodeRequest req(ConnectionType t, String url, Instant seen) {
        return new UpdateNodeRequest(null, null, null, null, null, null, null, t, url, seen);
    }

    @Test
    void oldNodeWithoutEndpointStillUpdatesOtherFields() {
        service.update(id, new UpdateNodeRequest(null, "Tên mới", null, null, "1.2.3", null, null, null, null, null));
        assertThat(node.getName()).isEqualTo("Tên mới");
        assertThat(node.getEndpointUrl()).isNull();
        assertThat(node.getConnectionType()).isNull();
    }

    @Test
    void savesAndUpdatesEndpoint() {
        service.update(id, req(ConnectionType.HTTP, " http://192.168.1.50:80 ", null));
        assertThat(node.getEndpointUrl()).isEqualTo("http://192.168.1.50:80");
        service.update(id, req(null, "http://192.168.1.51:80", Instant.now()));
        assertThat(node.getEndpointUrl()).isEqualTo("http://192.168.1.51:80");
        assertThat(node.getLastSeenAt()).isNotNull();
    }

    @Test
    void rejectsInvalidEndpointAndKeepsOldValue() {
        node.setConnectionType(ConnectionType.HTTP);
        node.setEndpointUrl("http://1.1.1.1");
        assertThatThrownBy(() -> service.update(id, req(null, "ftp://x", null)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "INVALID_ENDPOINT");
        assertThatThrownBy(() -> service.update(id, req(ConnectionType.HTTPS, null, null)))
                .isInstanceOf(BusinessException.class).hasFieldOrPropertyWithValue("code", "INVALID_ENDPOINT");
        assertThat(node.getEndpointUrl()).isEqualTo("http://1.1.1.1");
    }

    @Test
    void blankEndpointClearsConnection() {
        node.setConnectionType(ConnectionType.HTTP);
        node.setEndpointUrl("http://1.1.1.1");
        service.update(id, req(null, "", null));
        assertThat(node.getEndpointUrl()).isNull();
        assertThat(node.getConnectionType()).isNull();
    }

    @Test
    void rejectsFutureLastSeen() {
        assertThatThrownBy(() -> service.update(id, req(null, null, Instant.now().plusSeconds(3600))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void connectionReturnsData() {
        node.setConnectionType(ConnectionType.HTTP);
        node.setEndpointUrl("http://192.168.1.50");
        NodeConnectionResponse r = service.connection(id);
        assertThat(r.deviceUid()).isEqualTo("ESP32-AABB");
        assertThat(r.endpointUrl()).isEqualTo("http://192.168.1.50");
        assertThat(r.connectionType()).isEqualTo(ConnectionType.HTTP);
        assertThat(r.status()).isEqualTo(NodeStatus.ACTIVE);
    }

    @Test
    void connectionNodeNotFound() {
        UUID other = UUID.randomUUID();
        when(nodes.findById(other)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.connection(other))
                .hasFieldOrPropertyWithValue("code", "NODE_NOT_FOUND");
    }

    @Test
    void connectionNodeNotActive() {
        node.setConnectionType(ConnectionType.HTTP);
        node.setEndpointUrl("http://192.168.1.50");
        node.setStatus(NodeStatus.MAINTENANCE);
        assertThatThrownBy(() -> service.connection(id))
                .hasFieldOrPropertyWithValue("code", "NODE_NOT_ACTIVE");
    }

    @Test
    void connectionWithoutEndpoint() {
        assertThatThrownBy(() -> service.connection(id))
                .hasFieldOrPropertyWithValue("code", "NODE_ENDPOINT_NOT_SET");
    }
}
