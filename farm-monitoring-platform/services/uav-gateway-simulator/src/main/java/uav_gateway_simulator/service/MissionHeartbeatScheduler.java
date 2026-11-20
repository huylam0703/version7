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
    private final RecoveryLeaseRegistry leaseRegistry;
    private final MissionCancellationService cancellationService;

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

        // Nếu execution đang Recovery, lease phải hợp lệ.
        leaseRegistry.getForWrite(executionId);

        activeHeartbeats.computeIfAbsent(executionId, id -> {

            log.info(
                    "Starting heartbeat: missionId={}, executionId={}",
                    missionId,
                    executionId
            );

            return heartbeatExecutor.scheduleWithFixedDelay(
                    () -> {
                        try {
                            // Kiểm tra lease trước mỗi Heartbeat.
                            leaseRegistry.getForWrite(executionId);

                            missionClient.sendHeartbeat(
                                    missionId,
                                    executionId
                            );

                            log.debug(
                                    "Heartbeat confirmed: missionId={}, executionId={}",
                                    missionId,
                                    executionId
                            );

                        }
                        catch (org.springframework.web.client.RestClientResponseException e) {

                            int status = e.getStatusCode().value();

                            if (status == 403 || status == 409) {

                                log.error(
                                        "Heartbeat rejected: executionId={}, HTTP status={}",
                                        executionId,
                                        status
                                );

                                leaseRegistry.markLost(executionId);
                                stop(executionId);

                            } else if (status == 400) {

                                try {
                                    if (cancellationService.isCancelled(missionId)) {

                                        log.info(
                                                "Heartbeat stopped because mission was cancelled: missionId={}, executionId={}",
                                                missionId,
                                                executionId
                                        );

                                        stop(executionId);

                                    } else {

                                        log.error(
                                                "Heartbeat HTTP 400 but mission is not cancelled: missionId={}, executionId={}",
                                                missionId,
                                                executionId,
                                                e
                                        );
                                    }

                                } catch (Exception checkException) {

                                    log.error(
                                            "Cannot verify mission cancellation after heartbeat HTTP 400: missionId={}, executionId={}",
                                            missionId,
                                            executionId,
                                            checkException
                                    );
                                }

                            } else {

                                log.error(
                                        "Heartbeat HTTP error: executionId={}, status={}",
                                        executionId,
                                        status,
                                        e
                                );
                            }
                        }
                        catch (IllegalStateException e) {

                            log.error(
                                    "Heartbeat stopped due to invalid local state: executionId={}",
                                    executionId,
                                    e
                            );

                            stop(executionId);

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