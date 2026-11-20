
package uav_gateway_simulator.exception;

public class MissionCancelledException
        extends RuntimeException {

    public MissionCancelledException(String message) {
        super(message);
    }
}
