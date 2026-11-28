
package uav_gateway_simulator.service;

import uav_gateway_simulator.telemetry.FlightOwnershipToken;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

final class HeartbeatSession {

    private final FlightOwnershipToken ownershipToken;

    private final AtomicInteger consecutiveFailures =
            new AtomicInteger(0);

    private ScheduledFuture<?> future;

    private volatile boolean cancelled;

    HeartbeatSession(
            FlightOwnershipToken ownershipToken
    ) {
        this.ownershipToken =
                Objects.requireNonNull(ownershipToken);
    }

    FlightOwnershipToken ownershipToken() {
        return ownershipToken;
    }

    AtomicInteger consecutiveFailures() {
        return consecutiveFailures;
    }

    boolean isCancelled() {
        return cancelled;
    }

    synchronized void attachFuture(
            ScheduledFuture<?> future
    ) {
        this.future = Objects.requireNonNull(future);

        if (cancelled) {
            future.cancel(false);
        }
    }

    synchronized void cancel() {

        cancelled = true;

        if (future != null) {
            future.cancel(false);
        }
    }
}
