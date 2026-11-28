package vn.farm.sensordata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class ReadingRedisCache {
    private static final Logger LOG = LoggerFactory.getLogger(ReadingRedisCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;
    private final String prefix;
    private final String generationKey;
    private final AtomicReference<Object> pendingInvalidation = new AtomicReference<>();

    ReadingRedisCache(StringRedisTemplate redis, ObjectMapper mapper,
                      @Value("${SENSOR_CACHE_ENABLED:false}") boolean enabled,
                      @Value("${SENSOR_CACHE_TTL_SECONDS:15}") long ttlSeconds,
                      @Value("${SENSOR_CACHE_NAMESPACE:farm:sensor-data}") String namespace) {
        if (ttlSeconds < 1 || namespace.isBlank()) {
            throw new IllegalArgumentException("Sensor cache needs a positive TTL and a non-empty namespace");
        }
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.prefix = namespace + ":cache:v1:";
        this.generationKey = prefix + "generation";
    }

    <T> T getOrLoad(String query, Object arguments, TypeReference<T> type, Supplier<T> load) {
        if (!enabled || TransactionSynchronizationManager.isActualTransactionActive()) {
            return load.get();
        }

        String key = null;
        try {
            applyPendingInvalidation();
            String generation = readGeneration();
            if (generation != null) {
                key = prefix + generation + ":" + query + ":" + argumentHash(arguments);
                String json = redis.opsForValue().get(key);
                if (json != null) {
                    T cached = mapper.readValue(json, type);
                    if (cached != null) {
                        return cached;
                    }
                }
            }
        } catch (DataAccessException | JsonProcessingException e) {
            LOG.debug("Sensor Redis cache lookup failed: {}", e.getClass().getSimpleName());
        }

        T result = load.get();
        if (key != null) {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(result), ttl);
            } catch (DataAccessException | JsonProcessingException e) {
                LOG.debug("Sensor Redis cache write failed: {}", e.getClass().getSimpleName());
            }
        }
        return result;
    }

    void readingsChanged() {
        if (!enabled) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private void invalidate() {
        pendingInvalidation.set(new Object());
        try {
            applyPendingInvalidation();
        } catch (DataAccessException e) {
            LOG.debug("Sensor Redis cache invalidation pending: {}", e.getClass().getSimpleName());
        }
    }

    private void applyPendingInvalidation() {
        Object pending = pendingInvalidation.get();
        if (pending != null) {
            // Existing result keys expire by TTL. An in-flight query can only fill its old generation.
            redis.delete(generationKey);
            pendingInvalidation.compareAndSet(pending, null);
        }
    }

    private String readGeneration() {
        var values = redis.opsForValue();
        String current = values.get(generationKey);
        if (current != null) {
            return current;
        }
        String next = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(values.setIfAbsent(generationKey, next)) ? next : values.get(generationKey);
    }

    private String argumentHash(Object arguments) throws JsonProcessingException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(arguments));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
