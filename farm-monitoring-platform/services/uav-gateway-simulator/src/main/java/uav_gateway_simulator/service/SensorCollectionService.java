package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import uav_gateway_simulator.client.SensorDataClient;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorCollectionService {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_BATCHES_PER_REPLAY = 32;
    private final SensorDataClient sensorDataClient;
    private final SensorReadingOutbox outbox;

    public enum Delivery {
        SYNCHRONIZED,
        QUEUED
    }

    public Delivery collectAndSend(List<SensorReadingRequest> readings) {
        validateBatch(readings);
        List<SensorReadingRequest> batch = List.copyOf(readings);
        final Path entry;
        try {
            entry = outbox.saveAndClaim(batch);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Could not save sensor readings to disk", e);
        }

        try {
            for (int attempt = 1; ; attempt++) {
                try {
                    int invalid = upload(batch);
                    acknowledge(entry);
                    if (invalid > 0) {
                        throw new IllegalStateException("Sensor Data stored " + invalid + " INVALID reading(s)");
                    }
                    return Delivery.SYNCHRONIZED;
                } catch (RestClientException e) {
                    if (isPayloadError(e)) {
                        reject(entry, e);
                        throw e;
                    }
                    if (attempt == MAX_ATTEMPTS || !canRetryImmediately(e) || !waitBeforeRetry(attempt)) {
                        log.warn("Sensor batch queued for retry: file={}, readings={}, error={}",
                                entry.getFileName(), batch.size(), e.getMessage());
                        return Delivery.QUEUED;
                    }
                }
            }
        } finally {
            outbox.release(entry);
        }
    }

    @Scheduled(scheduler = "sensorSyncScheduler",
            initialDelayString = "${simulator.sensor-sync.retry-delay-ms:5000}",
            fixedDelayString = "${simulator.sensor-sync.retry-delay-ms:5000}")
    public void retryPending() {
        try (var entries = outbox.pending()) {
            int processed = 0;
            for (Path entry : entries) {
                if (!outbox.claim(entry)) {
                    continue;
                }
                try {
                    if (!replay(entry) || ++processed == MAX_BATCHES_PER_REPLAY) {
                        return;
                    }
                } finally {
                    outbox.release(entry);
                }
            }
        } catch (NoSuchFileException ignored) {
            // The outbox is created when the first node is collected.
        } catch (IOException | DirectoryIteratorException e) {
            log.warn("Could not scan the sensor outbox", e);
        }
    }

    private boolean replay(Path entry) throws IOException {
        try {
            var readings = outbox.read(entry);
            validateBatch(readings);
            int invalid = upload(readings);
            acknowledge(entry);
            log.info("Sensor batch synchronized: file={}, readings={}, invalid={}",
                    entry.getFileName(), readings.size(), invalid);
            return true;
        } catch (NoSuchFileException ignored) {
            // The immediate upload may have acknowledged this entry after the directory scan.
            return true;
        } catch (JacksonException | IllegalArgumentException e) {
            reject(entry, e);
            return true;
        } catch (RestClientException e) {
            if (isPayloadError(e)) {
                reject(entry, e);
                return true;
            }
            log.warn("Sensor batch still pending: file={}, error={}", entry.getFileName(), e.getMessage());
            return false;
        }
    }

    private int upload(List<SensorReadingRequest> batch) {
        var response = sensorDataClient.sendBatch(batch);
        int size = batch.size();
        if (response == null || response.total() != size || response.items() == null
                || response.items().size() != size) {
            throw new RestClientException("Incomplete Sensor Data batch response");
        }
        boolean[] seen = new boolean[size];
        int valid = 0;
        int duplicates = 0;
        int invalid = 0;
        for (var item : response.items()) {
            if (item == null || item.index() == null || item.index() < 0 || item.index() >= size
                    || seen[item.index()] || item.status() == null) {
                throw new RestClientException("Invalid item in Sensor Data batch response");
            }
            if (!"DUPLICATE".equals(item.status()) && (item.id() == null || item.id() <= 0)) {
                throw new RestClientException("Missing stored reading ID in Sensor Data batch response");
            }
            seen[item.index()] = true;
            switch (item.status()) {
                case "VALID" -> valid++;
                case "DUPLICATE" -> duplicates++;
                case "INVALID" -> invalid++;
                default -> throw new RestClientException("Unexpected Sensor Data status: " + item.status());
            }
        }
        if (valid != response.validCount() || duplicates != response.duplicateCount()
                || invalid != response.invalidCount()) {
            throw new RestClientException("Inconsistent Sensor Data batch response counts");
        }
        return invalid;
    }

    private void acknowledge(Path entry) {
        try {
            outbox.acknowledge(entry);
        } catch (IOException e) {
            log.warn("Sensor batch acknowledged by server but still on disk: file={}", entry.getFileName(), e);
        }
    }

    private void reject(Path entry, Exception cause) {
        try {
            outbox.reject(entry);
            log.warn("Sensor batch retained for inspection: file={}, error={}", entry.getFileName(), cause.getMessage());
        } catch (IOException e) {
            log.warn("Could not move rejected sensor batch: file={}", entry.getFileName(), e);
        }
    }

    private boolean isPayloadError(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 400 || status == 413 || status == 422;
        }
        return false;
    }

    private boolean canRetryImmediately(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            var status = response.getStatusCode();
            return status.value() == 429 || status.value() == 408 || status.is5xxServerError();
        }
        return true;
    }

    private boolean waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(300L * attempt);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void validateBatch(List<SensorReadingRequest> readings) {
        if (readings == null || readings.isEmpty() || readings.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("A sensor batch must contain 1 to " + MAX_BATCH_SIZE + " readings");
        }
        readings.forEach(this::validateReading);
    }

    private void validateReading(SensorReadingRequest reading) {
        if (reading == null) {
            throw new IllegalArgumentException("Sensor reading must not be null");
        }
        if (reading.nodeId() == null || reading.nodeId().isBlank()) {
            throw new IllegalArgumentException("Node ID is required");
        }
        if (reading.sensorType() == null || reading.sensorType().isBlank()) {
            throw new IllegalArgumentException("Sensor type is required");
        }
        if (reading.sensorValue() == null || !Double.isFinite(reading.sensorValue())) {
            throw new IllegalArgumentException("Sensor value must be finite");
        }
        if (reading.measuredAt() == null || reading.collectedAt() == null) {
            throw new IllegalArgumentException("Sensor timestamps are required");
        }
        if (reading.latitude() == null || !Double.isFinite(reading.latitude())
                || reading.latitude() < -90 || reading.latitude() > 90
                || reading.longitude() == null || !Double.isFinite(reading.longitude())
                || reading.longitude() < -180 || reading.longitude() > 180) {
            throw new IllegalArgumentException("Invalid sensor coordinates");
        }
    }
}
