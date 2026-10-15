package vn.farm.starter.web;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.farm.core.BusinessException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Lỗi validate body (@Valid): sinh từ ràng buộc trong file OpenAPI. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getField(),
                        LinkedHashMap::new,
                        Collectors.mapping(e -> String.valueOf(e.getDefaultMessage()), Collectors.toList())));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu không hợp lệ.", errors);
    }

    /** Lỗi validate tham số path/query. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onInvalidParam(ConstraintViolationException ex) {
        Map<String, List<String>> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        v -> v.getPropertyPath().toString(),
                        LinkedHashMap::new,
                        Collectors.mapping(v -> v.getMessage(), Collectors.toList())));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu không hợp lệ.", errors);
    }

    /** Lỗi nghiệp vụ do tầng service ném ra. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail onBusiness(BusinessException ex) {
        HttpStatus status = switch (ex.getType()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không đủ quyền truy cập.", null);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống", null);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, Map<String, List<String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setType(URI.create("urn:farm:error:" + code));
        pd.setProperty("code", code);
        pd.setProperty("traceId", MDC.get("traceId"));
        if (errors != null) pd.setProperty("errors", errors);
        return pd;
    }
}