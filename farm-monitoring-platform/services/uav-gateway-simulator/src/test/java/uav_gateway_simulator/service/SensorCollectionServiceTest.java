package uav_gateway_simulator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uav_gateway_simulator.client.Esp32Client;
import uav_gateway_simulator.client.SensorDataClient;
import uav_gateway_simulator.client.SensorDataClient.SyncItem;
import uav_gateway_simulator.client.SensorDataClient.SyncResponse;
import uav_gateway_simulator.config.SensorSyncConfig;
import uav_gateway_simulator.dto.request.SensorReadingRequest;
import uav_gateway_simulator.dto.response.WaypointResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uav_gateway_simulator.service.SensorCollectionService.Delivery.QUEUED;
import static uav_gateway_simulator.service.SensorCollectionService.Delivery.SYNCHRONIZED;

@ExtendWith(MockitoExtension.class)
class SensorCollectionServiceTest {

    @Mock
    private SensorDataClient sensorDataClient;

    @TempDir
    private Path directory;

    private final ObjectMapper mapper = new JsonMapper();
    private SensorReadingOutbox outbox;
    private SensorCollectionService service;
    private List<SensorReadingRequest> batch;

    @BeforeEach
    void setUp() {
        outbox = new SensorReadingOutbox(directory.toString(), mapper);
        service = new SensorCollectionService(sensorDataClient, outbox);
        var time = LocalDateTime.parse("2026-11-28T03:00:00.123");
        batch = List.of(new SensorReadingRequest("node-1", "WATER_LEVEL", 1000d,
                time, time.plusSeconds(1), 10.03, 105.78, "ADC_COUNT", "ADC_RAW", 12));
    }

    @Test
    void persistsBatchBeforeUploadingAndRemovesItAfterAcknowledgment() throws IOException {
        when(sensorDataClient.sendBatch(batch)).thenAnswer(invocation -> {
            assertEquals(1, countFiles(".json"));
            return valid();
        });

        assertEquals(SYNCHRONIZED, service.collectAndSend(batch));
        assertEquals(0, countFiles(".json"));
        verify(sensorDataClient).sendBatch(batch);
    }

    @Test
    void rejectsInvalidInputBeforeWritingOrSending() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> service.collectAndSend(null));
        assertThrows(IllegalArgumentException.class, () -> service.collectAndSend(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.collectAndSend(Arrays.asList((SensorReadingRequest) null)));
        verifyNoInteractions(sensorDataClient);
        assertEquals(0, countFiles(".json"));
    }

    @Test
    void refusesToReportSuccessIfDiskWriteFails() throws IOException {
        Path file = Files.createFile(directory.resolve("not-a-directory"));
        var unavailable = new SensorReadingOutbox(file.toString(), mapper);

        assertThrows(IllegalStateException.class,
                () -> new SensorCollectionService(sensorDataClient, unavailable).collectAndSend(batch));
        verifyNoInteractions(sensorDataClient);
    }

    @Test
    void retriesTransientHttpErrorsAndAcceptsDuplicates() throws IOException {
        when(sensorDataClient.sendBatch(batch))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(duplicate());

        assertEquals(SYNCHRONIZED, service.collectAndSend(batch));
        verify(sensorDataClient, times(3)).sendBatch(batch);
        assertEquals(0, countFiles(".json"));
    }

    @Test
    void automaticallyReplaysAfterRestartAndPreservesOriginalPayload() throws IOException {
        when(sensorDataClient.sendBatch(batch)).thenThrow(new ResourceAccessException("Connection refused"));
        assertEquals(QUEUED, service.collectAndSend(batch));
        verify(sensorDataClient, times(3)).sendBatch(batch);
        assertEquals(1, countFiles(".json"));

        var recoveredClient = mock(SensorDataClient.class);
        when(recoveredClient.sendBatch(batch)).thenReturn(duplicate());
        try (var restarted = new AnnotationConfigApplicationContext()) {
            restarted.getEnvironment().getPropertySources().addFirst(new MapPropertySource("sensor-test", Map.of(
                    "simulator.sensor-sync.directory", directory.toString(),
                    "simulator.sensor-sync.retry-delay-ms", "25"
            )));
            restarted.registerBean(SensorDataClient.class, () -> recoveredClient);
            restarted.registerBean(ObjectMapper.class, () -> new JsonMapper());
            restarted.register(SensorSyncConfig.class, EnableTestScheduling.class,
                    SensorReadingOutbox.class, SensorCollectionService.class);
            restarted.refresh();

            await().atMost(5, SECONDS).until(() -> countFiles(".json") == 0);
            verify(recoveredClient).sendBatch(batch);
        }
    }

    @Test
    void retriesIncompleteAcknowledgmentAndRetainsTheBatch() throws IOException {
        var second = new SensorReadingRequest("node-1", "SOIL_MOISTURE", 2425d,
                batch.getFirst().measuredAt(), batch.getFirst().collectedAt(), 10.03, 105.78,
                "ADC_COUNT", "ADC_RAW", 12);
        var readings = List.of(batch.getFirst(), second);
        var firstItem = new SyncItem(0, "VALID", 1L);
        when(sensorDataClient.sendBatch(readings)).thenReturn(new SyncResponse(2, 2, 0, 0, List.of(firstItem, firstItem)));

        assertEquals(QUEUED, service.collectAndSend(readings));
        verify(sensorDataClient, times(3)).sendBatch(readings);
        assertEquals(1, countFiles(".json"));
    }

    @Test
    void quarantinesMalformedPayloadAndContinuesSendingOtherBatches() throws IOException {
        when(sensorDataClient.sendBatch(batch))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .thenReturn(valid());

        assertThrows(HttpClientErrorException.class, () -> service.collectAndSend(batch));
        assertEquals(0, countFiles(".json"));
        assertEquals(1, countFiles(".rejected"));
        verify(sensorDataClient).sendBatch(batch);

        Files.writeString(directory.resolve("corrupt.json"), "{");
        outbox.release(outbox.saveAndClaim(batch));
        service.retryPending();
        assertEquals(0, countFiles(".json"));
        assertEquals(2, countFiles(".rejected"));
    }

    @Test
    void doesNotRetryAnInvalidReadingAlreadyStoredBySensorData() throws IOException {
        when(sensorDataClient.sendBatch(batch))
                .thenReturn(new SyncResponse(1, 0, 1, 0, List.of(new SyncItem(0, "INVALID", 1L))));

        var error = assertThrows(IllegalStateException.class, () -> service.collectAndSend(batch));
        assertTrue(error.getMessage().contains("INVALID"));
        verify(sensorDataClient).sendBatch(batch);
        assertEquals(0, countFiles(".json"));
    }

    @Test
    void savesAllReadingsFromTheEsp32WhenTheServerIsOffline() throws IOException {
        var esp32 = mock(Esp32Client.class);
        var nodeId = UUID.randomUUID();
        var waypoint = new WaypointResponse(UUID.randomUUID(), 1, new BigDecimal("10.03"),
                new BigDecimal("105.78"), null, null, List.of(nodeId));
        when(esp32.readByNodeId(nodeId))
                .thenReturn(mapper.readTree("""
                        {"node":"NODE_1","waterLevelRaw":0,"soilMoistureRaw":2425}
                        """));
        when(sensorDataClient.sendBatch(anyList()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        var cancellation = mock(MissionCancellationService.class);
        var missionId = UUID.randomUUID();
        var collector = new WaypointSensorCollectionService(esp32, new Esp32ReadingMapper(), service,
                cancellation, mock(RecoveryLeaseRegistry.class));
        var result = collector.collect(missionId, UUID.randomUUID(), waypoint).getFirst();

        assertEquals("SUCCESS", result.result());
        assertEquals(2, result.recordsCount());
        verify(cancellation).checkNotCancelled(missionId);
        try (var pending = outbox.pending()) {
            var saved = outbox.read(pending.iterator().next());
            assertEquals(List.of("WATER_LEVEL", "SOIL_MOISTURE"),
                    saved.stream().map(SensorReadingRequest::sensorType).toList());
            assertEquals(0.0, saved.getFirst().sensorValue());
            assertEquals(saved.getFirst().measuredAt(), saved.getLast().measuredAt());
        }
    }

    private long countFiles(String suffix) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(suffix)).count();
        }
    }

    private SyncResponse valid() {
        return new SyncResponse(1, 1, 0, 0, List.of(new SyncItem(0, "VALID", 1L)));
    }

    private SyncResponse duplicate() {
        return new SyncResponse(1, 0, 0, 1, List.of(new SyncItem(0, "DUPLICATE", null)));
    }

    @EnableScheduling
    static class EnableTestScheduling {}
}
