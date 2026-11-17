package uav_gateway_simulator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.client.MissionClient;

import java.util.UUID;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionHeartbeatScheduler {

    private final MissionClient missionClient;
    private final ScheduledExecutorService heartbeatExecutor;

    private final ConcurrentMap<UUID, ScheduledFuture<?>> activeHeartbeats =
            new ConcurrentHashMap<>();

    @Value("${simulator.heartbeat.interval-ms:10000}")
    private long heartbeatIntervalMs;

    public void start(UUID missionId, UUID executionId) {

        if (missionId == null || executionId == null) {
            throw new IllegalArgumentException(
                    "MissionId and executionId are required"
            );
        }

        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException(
                    "Heartbeat interval must be positive"
            );
        }

        activeHeartbeats.computeIfAbsent(executionId, id -> {

            log.info(
                    "Starting heartbeat: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

            return heartbeatExecutor.scheduleWithFixedDelay(
                    () -> {
                        try {
                            missionClient.sendHeartbeat(
                                    missionId,
                                    executionId
                            );

                            log.debug(
                                    "Heartbeat sent: executionId={}",
                                    executionId
                            );

                        } catch (Exception e) {
                            log.error(
                                    "Heartbeat failed: executionId={}",
                                    executionId,
                                    e
                            );
                        }
                    },
                    0,
                    heartbeatIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        });
    }

    public void stop(UUID executionId) {

        ScheduledFuture<?> future =
                activeHeartbeats.remove(executionId);

        if (future != null) {
            future.cancel(false);

            log.info(
                    "Heartbeat stopped: executionId={}",
                    executionId
            );
        }
    }
}