package uav_gateway_simulator.exception;

public class RecoveryOwnershipLostException
        extends IllegalStateException {

    public RecoveryOwnershipLostException(String message) {
        super(message);
    }

    public RecoveryOwnershipLostException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}