package uav_gateway_simulator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import uav_gateway_simulator.dto.request.MissionNodeResultRequest;
import uav_gateway_simulator.dto.request.MissionResultRequest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MissionResultPayloadPersistenceTest {
    @TempDir
    Path directory;

    @Test
    void keepsOriginalPayloadAcrossRestartUntilRemoved() {
        var mapper = JsonMapper.builder().build();
        var missionId = UUID.randomUUID();
        var executionId = UUID.randomUUID();
        var node = new MissionNodeResultRequest(UUID.randomUUID(), "SUCCESS", 2, 1,
                Instant.parse("2026-11-27T08:00:00Z"), null, null);
        var request = new MissionResultRequest(executionId, List.of(node), true);
        new MissionResultPayloadStore(mapper, directory).saveIfAbsent(missionId, request);

        var restarted = new MissionResultPayloadStore(mapper, directory);
        assertEquals(request, restarted.get(executionId).request());
        var replacement = new MissionResultRequest(executionId, List.of(), false);
        assertEquals(request, restarted.saveIfAbsent(missionId, replacement).request());
        assertThrows(IllegalStateException.class,
                () -> restarted.saveIfAbsent(UUID.randomUUID(), replacement));
        restarted.remove(executionId);

        assertNull(new MissionResultPayloadStore(mapper, directory).get(executionId));
    }
}
