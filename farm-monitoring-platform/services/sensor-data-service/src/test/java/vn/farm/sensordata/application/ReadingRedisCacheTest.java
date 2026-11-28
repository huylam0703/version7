package vn.farm.sensordata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.farm.sensordata.api.dto.ReadingFilter;
import vn.farm.sensordata.api.dto.ReadingPageRequest;
import vn.farm.sensordata.api.dto.ReadingResults.Page;
import vn.farm.sensordata.api.dto.ReadingSubmission;
import vn.farm.sensordata.domain.ReadingValueKind;
import vn.farm.sensordata.domain.SensorReading;
import vn.farm.sensordata.persistence.ReadingQueryRepository;
import vn.farm.sensordata.persistence.SensorReadingRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadingRedisCacheTest {
    private static final String PREFIX = "sensor-test:cache:v1:";
    private static final TypeReference<Integer> NUMBER = new TypeReference<>() {};
    private static final String GENERATION = PREFIX + "generation";
    private static final Duration TTL = Duration.ofSeconds(15);
    private static final Instant MEASURED_AT = Instant.parse("2026-11-29T02:00:00Z");

    private final Map<String, String> values = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ReadingRedisCache cache = new ReadingRedisCache(redis, mapper, true, 15, "sensor-test");
    private boolean connected = true;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(call -> {
            checkConnection();
            return values.get(call.getArgument(0));
        });
        when(ops.setIfAbsent(anyString(), anyString())).thenAnswer(call -> {
            checkConnection();
            return values.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null;
        });
        doAnswer(call -> {
            checkConnection();
            values.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), eq(TTL));
        when(redis.delete(anyString())).thenAnswer(call -> {
            checkConnection();
            return values.remove(call.getArgument(0)) != null;
        });
    }

    @Test
    void cachesLatestByFilterPaginationAndSort() {
        var repository = mock(ReadingQueryRepository.class);
        var service = new ReadingQueryService(repository, cache);
        var filter = new ReadingFilter("node-1", "LIGHT", UUID.randomUUID(), null, null, null,
                MEASURED_AT, null, null, null, null);
        var otherFarm = new ReadingFilter(filter.nodeId(), filter.sensorType(), UUID.randomUUID(), null,
                null, null, MEASURED_AT, null, null, null, null);
        var first = new ReadingPageRequest(1, 10, "measuredAt", "desc");
        var second = new ReadingPageRequest(2, 10, "measuredAt", "asc");
        when(repository.findPage(filter, first, "measured_at", false, true))
                .thenReturn(new Page(List.of(), 1, 10, 0));
        when(repository.findPage(filter, second, "measured_at", true, true))
                .thenReturn(new Page(List.of(), 2, 10, 0));
        when(repository.findPage(otherFarm, first, "measured_at", false, true))
                .thenReturn(new Page(List.of(), 1, 10, 1));

        assertEquals(service.list(filter, first, true), service.list(filter, first, true));
        assertEquals(2, service.list(filter, second, true).page());
        assertEquals(1, service.list(otherFarm, first, true).total());
        verify(repository, times(1)).findPage(filter, first, "measured_at", false, true);
        assertEquals(4, values.size());
    }

    @Test
    void lateReadFromAnOldGenerationCannotRestoreStaleResults() {
        assertEquals(10, cache.getOrLoad("statistics", "light", NUMBER, () -> {
            cache.readingsChanged();
            return 10;
        }));
        assertEquals(20, cache.getOrLoad("statistics", "light", NUMBER, () -> 20));
        assertEquals(20, cache.getOrLoad("statistics", "light", NUMBER, () -> 30));
    }

    @Test
    void readsFromSqlDuringOutageAndClearsStaleCacheAfterRecovery() {
        assertEquals(10, cache.getOrLoad("statistics", "light", NUMBER, () -> 10));
        String originalGeneration = values.get(GENERATION);
        connected = false;
        cache.readingsChanged();
        assertEquals(20, cache.getOrLoad("statistics", "light", NUMBER, () -> 20));
        connected = true;
        assertEquals(30, cache.getOrLoad("statistics", "light", NUMBER, () -> 30));
        assertEquals(30, cache.getOrLoad("statistics", "light", NUMBER, () -> 40));
        assertNotEquals(originalGeneration, values.get(GENERATION));
    }

    @Test
    void skipsRedisWhenDisabled() {
        var disabled = new ReadingRedisCache(redis, mapper, false, 15, "sensor-test");
        disabled.readingsChanged();
        assertEquals(10, disabled.getOrLoad("statistics", "light", NUMBER, () -> 10));
        assertEquals(20, disabled.getOrLoad("statistics", "light", NUMBER, () -> 20));
        verifyNoInteractions(redis);
    }

    @Test
    void waitsUntilAnEnclosingSqlTransactionCommits() {
        cache.getOrLoad("latest", "light", NUMBER, () -> 10);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.readingsChanged();
            assertTrue(values.containsKey(GENERATION));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertEquals(20, cache.getOrLoad("latest", "light", NUMBER, () -> 20));
    }

    @Test
    void invalidatesResultsWhenAPartiallyStoredBatchFails() {
        var repository = mock(SensorReadingRepository.class);
        var service = new SensorReadingService(repository, cache);
        var stored = mock(SensorReading.class);
        when(stored.getId()).thenReturn(1L);
        when(repository.saveAndFlush(any(SensorReading.class))).thenReturn(stored)
                .thenThrow(new DataAccessResourceFailureException("SQL unavailable"));

        cache.getOrLoad("statistics", "light", NUMBER, () -> 10);
        assertThrows(DataAccessResourceFailureException.class,
                () -> service.saveBatch(List.of(reading("node-1"), reading("node-2"))));
        assertEquals(20, cache.getOrLoad("statistics", "light", NUMBER, () -> 20));
    }

    private static ReadingSubmission reading(String nodeId) {
        return new ReadingSubmission(nodeId, "LIGHT", 50d, MEASURED_AT, MEASURED_AT,
                10d, 106d, "lux", ReadingValueKind.PHYSICAL, null, null, null, null, null);
    }

    private void checkConnection() {
        if (!connected) {
            throw new DataAccessResourceFailureException("Redis unavailable");
        }
    }
}
