package vn.farm.starter.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.farm.core.BusinessException;
import vn.farm.core.api.ApiResponse;

import java.util.stream.Collectors;

/** Shared error envelope: HTTP status + numeric application code. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, int code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .code(code).message(message).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, 1001, message.isBlank() ? "Invalid request" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> invalidParam(ConstraintViolationException ex) {
        return error(HttpStatus.BAD_REQUEST, 1001, "Invalid request parameters");
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        HttpStatus status = switch (ex.getType()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        int code = switch (ex.getType()) {
            case NOT_FOUND -> 1004;
            case CONFLICT -> 1009;
            case UNAUTHORIZED -> 1006;
            case FORBIDDEN -> 1007;
            case FAILURE -> 9999;
            default -> 1001;
        };
        // BusinessException.getCode() retains the domain-specific string code for logging.
        log.debug("Business error {}: {}", ex.getCode(), ex.getMessage());
        return error(status, code, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, 1007, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, 9999, "Internal server error");
    }
}
