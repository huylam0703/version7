package vn.farm.farmdevice.config;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.farm.core.BusinessException;

/**
 * Lỗi theo PLATFORM-CONVENTIONS §4: RFC 7807 (application/problem+json), `code` chữ in hoa ổn định.
 * Order cao nhất để chạy trước GlobalExceptionHandler của starter.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(WebErrorHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail onBusiness(BusinessException ex) {
        HttpStatus status = switch (ex.getType()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.BAD_REQUEST;
        };
        log.debug("Business error {}: {}", ex.getCode(), ex.getMessage());
        return problem(status, ex.getCode(), ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalidBody(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(),
                        "message", e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Dữ liệu gửi lên không hợp lệ.", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String code = UUID.class.equals(ex.getRequiredType()) ? "INVALID_UUID" : "INVALID_QUERY";
        return problem(HttpStatus.BAD_REQUEST, code, "Giá trị của '" + ex.getName() + "' không hợp lệ.", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Nội dung yêu cầu không đọc được hoặc có giá trị không hợp lệ.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không đủ quyền thực hiện thao tác này.", null);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, List<Map<String, String>> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setType(URI.create("https://farm.vn/errors/" + code.toLowerCase()));
        pd.setProperty("code", code);
        String traceId = MDC.get("traceId");
        if (traceId != null) pd.setProperty("traceId", traceId);
        if (errors != null && !errors.isEmpty()) pd.setProperty("errors", errors);
        return pd;
    }
}