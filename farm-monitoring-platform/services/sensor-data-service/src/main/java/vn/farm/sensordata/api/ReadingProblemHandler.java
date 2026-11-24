package vn.farm.sensordata.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = ReadingV1Controller.class)
public class ReadingProblemHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ReadingProblemHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = error.getBody();
        problem.setProperty("errors", error.getFieldErrors().stream()
                .map(field -> Map.of("field", field.getField(), "message",
                        field.getDefaultMessage() == null ? "Invalid value" : field.getDefaultMessage()))
                .toList());
        return handleExceptionInternal(error, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            decorate(problem);
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    @ExceptionHandler(DateTimeException.class)
    public ProblemDetail invalidTimestamp() {
        return decorate(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Timestamp is outside the valid range"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail persistenceFailure(DataAccessException failure) {
        ProblemDetail problem = decorate(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to complete the sensor data request"));
        log.error("Sensor data request failed. traceId={}", problem.getProperties().get("traceId"), failure);
        return problem;
    }

    private static ProblemDetail decorate(ProblemDetail problem) {
        String code = switch (problem.getStatus()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "READING_NOT_FOUND";
            case 500 -> "SENSOR_DATA_ERROR";
            default -> "REQUEST_FAILED";
        };
        String traceId = MDC.get("traceId");
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId);
        return problem;
    }
}
