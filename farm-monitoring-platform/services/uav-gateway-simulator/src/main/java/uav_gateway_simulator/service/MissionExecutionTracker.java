
package uav_gateway_simulator.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MissionExecutionTracker {

    public enum Status {
        PROCESSING,
        RESULTS_PENDING,
        RESULTS_RETRY,
        COMPLETED,
        FAILED
    }


    private final ConcurrentMap<UUID, Status> executions =
            new ConcurrentHashMap<>();

    public boolean tryStart(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        return executions.putIfAbsent(
                executionId,
                Status.PROCESSING
        ) == null;
    }

    public void markResultsPending(UUID executionId) {
        boolean updated = executions.replace(
                executionId,
                Status.PROCESSING,
                Status.RESULTS_PENDING
        );

        if (!updated) {
            throw new IllegalStateException(
                    "Cannot mark results pending: " + executionId
            );
        }
    }

    public void markResultsRetry(UUID executionId) {
        boolean updated = executions.replace(
                executionId,
                Status.RESULTS_PENDING,
                Status.RESULTS_RETRY
        );

        if (!updated) {
            throw new IllegalStateException(
                    "Cannot mark results retry: " + executionId
            );
        }
    }

    public boolean tryStartResultsRetry(UUID executionId) {
        return executions.replace(
                executionId,
                Status.RESULTS_RETRY,
                Status.RESULTS_PENDING
        );
    }


    public boolean complete(UUID executionId) {
        if (executions.replace(
                executionId,
                Status.RESULTS_PENDING,
                Status.COMPLETED
        )) {
            return true;
        }

        return executions.replace(
                executionId,
                Status.PROCESSING,
                Status.COMPLETED
        );
    }



    public void fail(UUID executionId) {
        executions.replace(
                executionId,
                Status.PROCESSING,
                Status.FAILED
        );
    }


    public Status getStatus(UUID executionId) {
        return executions.get(executionId);
    }


    public boolean releaseBeforeStart(UUID executionId) {
        return executions.remove(
                executionId,
                Status.PROCESSING
        );
    }


    public boolean tryStartRecovery(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        return executions.replace(
                executionId,
                Status.FAILED,
                Status.PROCESSING
        );
    }
    public boolean rollbackRecovery(UUID executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        return executions.replace(
                executionId,
                Status.PROCESSING,
                Status.FAILED
        );
    }
}
