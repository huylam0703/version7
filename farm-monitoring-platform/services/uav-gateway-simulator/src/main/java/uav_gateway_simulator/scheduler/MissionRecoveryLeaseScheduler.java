
package uav_gateway_simulator.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uav_gateway_simulator.exception.RecoveryOwnershipLostException;
import uav_gateway_simulator.service.MissionRecoveryLeaseManager;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MissionRecoveryLeaseScheduler {

    private final MissionRecoveryLeaseManager leaseManager;

    @Scheduled(fixedDelay = 20000)
    public void renewLeases() {

        for (UUID executionId : leaseManager.getActiveExecutionIds()) {

            try {

                // Check whether the current lease is still valid.
                leaseManager.requireValidLease(executionId);

                // Renew ownership with Mission Service.
                leaseManager.renew(executionId);

                log.debug(
                        "Recovery lease renewed: executionId={}",
                        executionId
                );

            } catch (RecoveryOwnershipLostException e) {

                // Explicit ownership rejection or invalid lease.
                leaseManager.markLost(executionId);

                log.error(
                        "Recovery ownership lost: executionId={}",
                        executionId,
                        e
                );

            } catch (IllegalStateException e) {

                // The lease may have expired or changed concurrently.
                // Check whether it is still usable.
                if (!leaseManager.hasValidLease(executionId)) {

                    leaseManager.markLost(executionId);

                    log.error(
                            "Recovery lease is no longer valid: executionId={}",
                            executionId,
                            e
                    );

                } else {

                    log.warn(
                            "Recovery lease renewal skipped: executionId={}",
                            executionId,
                            e
                    );
                }

            } catch (Exception e) {

                // Temporary network/server errors do not immediately
                // invalidate an otherwise valid lease.
                if (!leaseManager.hasValidLease(executionId)) {

                    leaseManager.markLost(executionId);

                    log.error(
                            "Recovery lease expired after renewal failure: executionId={}",
                            executionId,
                            e
                    );

                } else {

                    log.warn(
                            "Temporary recovery lease renewal failure. "
                                    + "Will retry on next cycle: executionId={}",
                            executionId,
                            e
                    );
                }
            }
        }
    }
}
