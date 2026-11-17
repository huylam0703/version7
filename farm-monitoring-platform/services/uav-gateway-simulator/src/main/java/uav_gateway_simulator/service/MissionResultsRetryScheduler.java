
package uav_gateway_simulator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MissionResultsRetryScheduler {

    private final MissionExecutionTracker executionTracker;
    private final MissionResultSimulationService resultSimulationService;
    private final MissionResultPayloadStore payloadStore;
    private final ScheduledExecutorService scheduler;

    private final ConcurrentMap<UUID, RetryState> retryStates =
            new ConcurrentHashMap<>();

    @Value("${simulator.results.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${simulator.results.retry.delay-ms:5000}")
    private long retryDelayMs;

    @Value("${simulator.results.retry.max-attempts:3}")
    private int maxAttempts;

    public MissionResultsRetryScheduler(
            MissionExecutionTracker executionTracker,
            MissionResultSimulationService resultSimulationService,
            MissionResultPayloadStore payloadStore,
            @Qualifier("missionResultsRetryExecutor")
            ScheduledExecutorService scheduler
    ) {
        this.executionTracker = executionTracker;
        this.resultSimulationService = resultSimulationService;
        this.payloadStore = payloadStore;
        this.scheduler = scheduler;
    }

    private static class RetryState {

        private final AtomicInteger attempts =
                new AtomicInteger(0);

        private boolean scheduled;
        private boolean running;
        private boolean exhausted;
    }

    public void schedule(UUID executionId) {
        if (!retryEnabled || executionId == null) {
            return;
        }

        if (executionTracker.getStatus(executionId)
                != MissionExecutionTracker.Status.RESULTS_RETRY) {
            return;
        }

        if (payloadStore.get(executionId) == null) {
            log.error(
                    "Cannot schedule retry: payload missing, executionId={}",
                    executionId
            );
            return;
        }

        RetryState state = retryStates.computeIfAbsent(
                executionId,
                id -> new RetryState()
        );

        synchronized (state) {

            if (state.scheduled
                    || state.running
                    || state.exhausted) {
                return;
            }

            if (state.attempts.get() >= maxAttempts) {
                state.exhausted = true;
                return;
            }

            state.scheduled = true;

            try {
                scheduler.schedule(
                        () -> executeRetry(executionId, state),
                        retryDelayMs,
                        TimeUnit.MILLISECONDS
                );

                log.info(
                        "Mission Results retry scheduled: executionId={}, delayMs={}",
                        executionId,
                        retryDelayMs
                );

            } catch (RuntimeException e) {
                state.scheduled = false;
                throw e;
            }
        }
    }

    private void executeRetry(
            UUID executionId,
            RetryState state
    ) {
        synchronized (state) {
            state.scheduled = false;

            if (state.running || state.exhausted) {
                return;
            }

            if (!executionTracker.tryStartResultsRetry(
                    executionId)) {
                return;
            }

            state.running = true;
        }

        int attempt = state.attempts.incrementAndGet();
        boolean succeeded = false;

        try {
            resultSimulationService.retryResults(executionId);

            if (!executionTracker.complete(executionId)) {
                throw new IllegalStateException(
                        "Cannot complete Mission Results retry: "
                                + executionId
                );
            }

            payloadStore.remove(executionId);
            succeeded = true;

            log.info(
                    "Mission Results retry succeeded: executionId={}, attempt={}",
                    executionId,
                    attempt
            );

        } catch (Exception e) {

            if (executionTracker.getStatus(executionId)
                    == MissionExecutionTracker.Status.RESULTS_PENDING) {

                executionTracker.markResultsRetry(executionId);
            }

            log.error(
                    "Mission Results retry failed: executionId={}, attempt={}",
                    executionId,
                    attempt,
                    e
            );

        } finally {
            synchronized (state) {
                state.running = false;

                if (!succeeded
                        && attempt >= maxAttempts) {
                    state.exhausted = true;
                }
            }
        }

        if (succeeded) {
            retryStates.remove(executionId, state);
            return;
        }

        if (attempt < maxAttempts) {
            schedule(executionId);
        } else {
            log.error(
                    "Mission Results retry exhausted: executionId={}, maxAttempts={}",
                    executionId,
                    maxAttempts
            );
        }
    }
}
