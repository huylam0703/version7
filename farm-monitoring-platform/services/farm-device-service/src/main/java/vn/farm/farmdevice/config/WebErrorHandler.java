package vn.farm.farmdevice.config;

import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Bổ sung cho GlobalExceptionHandler của starter (nó bắt Exception nên UUID sai / JSON sai sẽ thành 500).
 * Order cao nhất để chạy trước handler chung. Nên chuyển lên starter khi cả nhóm đồng ý.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebErrorHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String code = UUID.class.equals(ex.getRequiredType()) ? "INVALID_UUID" : "INVALID_QUERY";
        return problem(code, "Giá trị của '" + ex.getName() + "' không hợp lệ.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadable(HttpMessageNotReadableException ex) {
        return problem("VALIDATION_FAILED", "Nội dung yêu cầu không đọc được hoặc có giá trị không hợp lệ.");
    }

    private static ProblemDetail problem(String code, String title) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle(title);
        pd.setType(URI.create("urn:farm:error:" + code));
        pd.setProperty("code", code);
        pd.setProperty("traceId", MDC.get("traceId"));
        return pd;
    }
}
