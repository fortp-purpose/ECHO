package echo.platform.api;

import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Object value = request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE_NAME
        );
        String correlationId =
                value instanceof String id ? id : "unavailable";

        Map<String, Object> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage(),
                        (first, second) -> first
                ));

        ApiError body = new ApiError(
                "VALIDATION_ERROR",
                HttpStatus.BAD_REQUEST.value(),
                "One or more fields failed validation.",
                details,
                correlationId,
                Instant.now()
        );

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        Object value = request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE_NAME
        );
        String correlationId =
                value instanceof String id ? id : "unavailable";

        log.error(
                "Unhandled request failure. correlationId={}",
                correlationId,
                exception
        );

        ApiError body = new ApiError(
                "INTERNAL_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "The request could not be completed.",
                Map.of(),
                correlationId,
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}
