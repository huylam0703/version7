
package uav_gateway_simulator.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uav_gateway_simulator.dto.response.MissionResultResponse;
import uav_gateway_simulator.service.RecoveryLeaseRegistry;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FlightLifecycleService {

    private final FlightTelemetryService telemetryService;
    private final FlightOwnershipService flightOwnershipService;
    private final RecoveryLeaseRegistry leaseRegistry;

    public void onMissionResult(
            UUID missionId,
            UUID executionId,
            MissionResultResponse response
    ) {
        onMissionResult(
                missionId,
                executionId,
                response,
                flightOwnershipService.capture(executionId)
        );
    }

    public void onMissionResult(
            UUID missionId,
            UUID executionId,
            MissionResultResponse response,
            FlightOwnershipToken ownershipToken
    ) {
        Objects.requireNonNull(
                response,
                "Mission result is required"
        );

        if (!Objects.equals(missionId, response.missionId())
                || !Objects.equals(executionId, response.executionId())) {

            throw new IllegalArgumentException(
                    "Mission result identity mismatch"
            );
        }

        FlightStatus status = switch (response.status()) {
            case "COMPLETED" -> FlightStatus.COMPLETED;
            case "PARTIAL" -> FlightStatus.PARTIAL;
            case "FAILED" -> FlightStatus.FAILED;

            default -> throw new IllegalArgumentException(
                    "Invalid final Mission status: "
                            + response.status()
            );
        };

        updateIfPresent(
                missionId,
                executionId,
                status,
                ownershipToken
        );
    }

    public void onCancelled(
            UUID missionId,
            UUID executionId
    ) {
        onCancelled(
                missionId,
                executionId,
                flightOwnershipService.capture(executionId)
        );
    }

    public void onCancelled(
            UUID missionId,
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        updateIfPresent(
                missionId,
                executionId,
                FlightStatus.CANCELLED,
                ownershipToken
        );
    }

    public void onDisconnected(
            UUID missionId,
            UUID executionId
    ) {
        onDisconnected(
                missionId,
                executionId,
                flightOwnershipService.capture(executionId)
        );
    }

    public void onDisconnected(
            UUID missionId,
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        updateIfPresent(
                missionId,
                executionId,
                FlightStatus.DISCONNECTED,
                ownershipToken
        );
    }

    public void onRecovering(
            UUID missionId,
            UUID executionId
    ) {
        onRecovering(
                missionId,
                executionId,
                flightOwnershipService.capture(executionId)
        );
    }

    public void onRecovering(
            UUID missionId,
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        updateIfPresent(
                missionId,
                executionId,
                FlightStatus.RECOVERING,
                ownershipToken
        );
    }

    public void onReconnected(
            UUID missionId,
            UUID executionId
    ) {
        onReconnected(
                missionId,
                executionId,
                flightOwnershipService.capture(executionId)
        );
    }

    public void onReconnected(
            UUID missionId,
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnershipToken(
                executionId,
                ownershipToken
        );

        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> telemetryService.reconnectIfDisconnected(
                        missionId,
                        executionId
                )
        );
    }

    private void updateIfPresent(
            UUID missionId,
            UUID executionId,
            FlightStatus status,
            FlightOwnershipToken ownershipToken
    ) {
        validateOwnershipToken(
                executionId,
                ownershipToken
        );

        flightOwnershipService.executeIfCurrent(
                ownershipToken,
                () -> telemetryService.updateStatusIfPresent(
                        missionId,
                        executionId,
                        status
                )
        );
    }

    private void validateOwnershipToken(
            UUID executionId,
            FlightOwnershipToken ownershipToken
    ) {
        if (executionId == null
                || ownershipToken == null
                || !executionId.equals(
                ownershipToken.executionId()
        )) {

            throw new IllegalArgumentException(
                    "Flight lifecycle ownership mismatch"
            );
        }
    }

    public void onCancelledBeforeClaim(
            UUID missionId,
            UUID executionId
    ) {
        Objects.requireNonNull(
                missionId,
                "MissionId is required"
        );

        Objects.requireNonNull(
                executionId,
                "ExecutionId is required"
        );

        leaseRegistry.runBeforeClaim(
                executionId,
                () -> telemetryService.updateStatusIfPresent(
                        missionId,
                        executionId,
                        FlightStatus.CANCELLED
                )
        );
    }
}
