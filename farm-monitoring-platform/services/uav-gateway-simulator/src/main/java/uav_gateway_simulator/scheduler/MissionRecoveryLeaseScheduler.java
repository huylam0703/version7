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

                leaseManager.renew(executionId);

                log.debug(
                        "Recovery lease renewal processed: executionId={}",
                        executionId
                );

            } catch (RecoveryOwnershipLostException e) {

                // Lease Manager handles ownership loss
                // using the original ownership token.
                log.warn(
                        "Recovery ownership lost: executionId={}",
                        executionId,
                        e
                );

            } catch (IllegalStateException e) {

                // Do not invalidate another recovery generation.
                log.warn(
                        "Recovery lease renewal skipped: executionId={}",
                        executionId,
                        e
                );

            } catch (Exception e) {

                // Temporary errors will be retried on the next cycle.
                log.warn(
                        "Recovery lease renewal failed: executionId={}",
                        executionId,
                        e
                );
            }
        }
    }
}