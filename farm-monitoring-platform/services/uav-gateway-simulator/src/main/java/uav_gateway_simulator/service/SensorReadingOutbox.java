package uav_gateway_simulator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uav_gateway_simulator.dto.request.SensorReadingRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class SensorReadingOutbox {

    private final Path directory;
    private final ObjectMapper mapper;
    private final Set<Path> inFlight = ConcurrentHashMap.newKeySet();

    SensorReadingOutbox(@Value("${simulator.sensor-sync.directory}") String directory,
                        ObjectMapper mapper) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.mapper = mapper;
    }

    Path saveAndClaim(List<SensorReadingRequest> readings) throws IOException {
        Files.createDirectories(directory);
        var name = UUID.randomUUID().toString();
        Path pending = directory.resolve(name + ".json");
        Path temp = directory.resolve(name + ".tmp");
        byte[] contents = mapper.writeValueAsBytes(readings);

        inFlight.add(pending);
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(contents);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temp, pending, StandardCopyOption.ATOMIC_MOVE);
            return pending;
        } catch (IOException | RuntimeException e) {
            inFlight.remove(pending);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    DirectoryStream<Path> pending() throws IOException {
        return Files.newDirectoryStream(directory, "*.json");
    }

    boolean claim(Path entry) {
        return inFlight.add(entry);
    }

    void release(Path entry) {
        inFlight.remove(entry);
    }

    List<SensorReadingRequest> read(Path entry) throws IOException {
        var readings = mapper.readValue(Files.readAllBytes(entry), SensorReadingRequest[].class);
        if (readings == null) {
            throw new IllegalArgumentException("Missing readings in outbox entry");
        }
        return Arrays.asList(readings);
    }

    void acknowledge(Path entry) throws IOException {
        Files.deleteIfExists(entry);
    }

    void reject(Path entry) throws IOException {
        Files.move(entry, entry.resolveSibling(entry.getFileName() + ".rejected"),
                StandardCopyOption.ATOMIC_MOVE);
    }
}
