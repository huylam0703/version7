package uav_gateway_simulator.dto;

public record ApiResponse<T>(
        int code,
        String message,
        T result
) {
}