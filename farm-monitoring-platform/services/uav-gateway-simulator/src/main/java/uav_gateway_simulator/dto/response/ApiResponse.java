package uav_gateway_simulator.dto.response;

public record ApiResponse<T>(
        int code,
        String message,
        T result
) {
}