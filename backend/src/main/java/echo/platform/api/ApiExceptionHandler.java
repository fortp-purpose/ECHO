package echo.platform.api;

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

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        String correlationId = value instanceof String id ? id : "unavailable";

        // Details stay in server logs; never return exception text or a stack trace to clients.
        log.error("Unhandled request failure. correlationId={}", correlationId, exception);

        ApiError body = new ApiError(
                "INTERNAL_ERROR",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "The request could not be completed.",
                Map.of(),
                correlationId,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
