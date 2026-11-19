
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
    private final ConcurrentMap<UUID, Status> recoveryPreviousStatuses =
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

        if (executionId == null) {
            return;
        }

        executions.computeIfPresent(
                executionId,
                (id, status) -> {
                    if (status == Status.COMPLETED) {
                        return status;
                    }

                    if (status == Status.RESULTS_RETRY) {
                        return status;
                    }

                    return Status.FAILED;
                }
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

        Status existing = executions.putIfAbsent(
                executionId,
                Status.PROCESSING
        );

        if (existing == null) {
            return true;
        }

        if (executions.replace(
                executionId,
                Status.FAILED,
                Status.PROCESSING
        )) {
            recoveryPreviousStatuses.put(executionId, Status.FAILED);
            return true;
        }

        return false;
    }


    public boolean rollbackRecovery(UUID executionId) {

        if (executionId == null) {
            throw new IllegalArgumentException(
                    "ExecutionId must not be null"
            );
        }

        Status previousStatus =
                recoveryPreviousStatuses.remove(executionId);

        if (previousStatus == Status.FAILED) {
            return executions.replace(
                    executionId,
                    Status.PROCESSING,
                    Status.FAILED
            );
        }

        return executions.remove(
                executionId,
                Status.PROCESSING
        );
    }

    public void failDueToOwnershipLoss(UUID executionId) {

        if (executionId == null) {
            return;
        }

        executions.computeIfPresent(
                executionId,
                (id, status) ->
                        status == Status.COMPLETED
                                ? status
                                : Status.FAILED
        );
    }
    public void clearRecoveryTracking(UUID executionId) {
        if (executionId != null) {
            recoveryPreviousStatuses.remove(executionId);
        }
    }

}
