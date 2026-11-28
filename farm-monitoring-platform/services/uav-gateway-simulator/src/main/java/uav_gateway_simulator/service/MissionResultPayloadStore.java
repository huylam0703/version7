package uav_gateway_simulator.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uav_gateway_simulator.dto.request.MissionResultRequest;
import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class MissionResultPayloadStore {

    private final Map<UUID, PendingResult> pendingResults = new HashMap<>();
    private final ObjectMapper mapper;
    private final Path directory;

    // In-memory store for isolated unit tests.
    public MissionResultPayloadStore() {
        this.mapper = null;
        this.directory = null;
    }

    @Autowired
    public MissionResultPayloadStore(
            ObjectMapper mapper,
            @Value("${SIMULATOR_PENDING_RESULTS_DIR:./data/pending-results}") Path directory
    ) {
        this.mapper = mapper;
        this.directory = directory.toAbsolutePath();
    }

    public record PendingResult(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken
    ) {}

    public synchronized PendingResult saveIfAbsent(
            UUID missionId,
            MissionResultRequest request
    ) {
        validatePayload(missionId, request);

        return saveIfAbsent(
                missionId,
                request,
                new FlightOwnershipToken(
                        request.executionId(),
                        null,
                        null
                )
        );
    }

    public synchronized PendingResult saveIfAbsent(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken
    ) {
        validatePayload(missionId, request);

        if (ownershipToken == null
                || !request.executionId().equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Results ownership token mismatch"
            );
        }

        PendingResult pending = new PendingResult(
                missionId,
                request,
                ownershipToken
        );

        UUID executionId = request.executionId();
        PendingResult existing = get(executionId);

        if (existing != null) {
            if (!existing.missionId().equals(missionId)) {
                throw new IllegalStateException(
                        "ExecutionId belongs to another mission"
                );
            }

            if (!ownershipToken.equals(existing.ownershipToken())) {
                throw new IllegalStateException(
                        "Pending Results ownership mismatch"
                );
            }

            return existing;
        }

        if (directory == null) {
            pendingResults.put(executionId, pending);
        } else {
            write(executionId, pending);
        }
        return pending;
    }

    private void validatePayload(
            UUID missionId,
            MissionResultRequest request
    ) {
        if (missionId == null
                || request == null
                || request.executionId() == null) {
            throw new IllegalArgumentException(
                    "Invalid Mission Results payload"
            );
        }
    }

    public synchronized PendingResult get(UUID executionId) {
        if (directory == null) {
            return pendingResults.get(executionId);
        }
        try {
            PendingResult pending = mapper.readValue(
                    Files.readAllBytes(file(executionId)), PendingResult.class);
            if (pending == null || pending.missionId() == null || pending.request() == null
                    || !executionId.equals(pending.request().executionId())) {
                throw new IllegalStateException("Invalid stored Mission Results: " + executionId);
            }
            return pending;
        } catch (java.nio.file.NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read pending Mission Results: " + executionId, e);
        }
    }

    public synchronized void remove(UUID executionId) {
        if (directory == null) {
            pendingResults.remove(executionId);
            return;
        }
        try {
            Files.deleteIfExists(file(executionId));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot clear pending Mission Results: " + executionId, e);
        }
    }

    private Path file(UUID executionId) {
        return directory.resolve(executionId + ".json");
    }

    private void write(UUID executionId, PendingResult pending) {
        byte[] payload = mapper.writeValueAsBytes(pending);
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, executionId + "-", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(payload);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                    channel.force(true);
                }
                Files.move(temporary, file(executionId), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store pending Mission Results: " + executionId, e);
        }
    }

    public synchronized boolean remove(PendingResult expected) {
        if (expected == null) {
            return false;
        }
        UUID executionId = expected.request().executionId();
        if (!expected.equals(get(executionId))) {
            return false;
        }
        remove(executionId);
        return true;
    }

    public PendingResult saveForCurrentOwner(
            UUID missionId,
            MissionResultRequest request,
            FlightOwnershipToken ownershipToken,
            RecoveryLeaseRegistry leaseRegistry
    ) {
        validatePayload(missionId, request);

        if (ownershipToken == null
                || !request.executionId().equals(
                ownershipToken.executionId()
        )) {
            throw new IllegalArgumentException(
                    "Results ownership token mismatch"
            );
        }

        synchronized (leaseRegistry) {

            new uav_gateway_simulator.telemetry.FlightOwnershipService(
                    leaseRegistry
            ).validate(ownershipToken);

            synchronized (this) {
                UUID executionId = request.executionId();
                PendingResult existing = get(executionId);
                if (existing != null) {
                    if (!existing.missionId().equals(missionId)) {
                        throw new IllegalStateException(
                                "ExecutionId belongs to another mission"
                        );
                    }
                    if (ownershipToken.equals(existing.ownershipToken())) {
                        return existing;
                    }
                }
                PendingResult pending = new PendingResult(missionId, request, ownershipToken);
                if (directory == null) {
                    pendingResults.put(executionId, pending);
                } else {
                    write(executionId, pending);
                }
                return pending;
            }
        }
    }
}
