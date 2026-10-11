package echo.catalog.api;

import echo.catalog.application.CatalogProviderUnavailableException;
import echo.catalog.application.CatalogResourceNotFoundException;
import echo.platform.api.ApiError;
import echo.platform.api.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Maps catalogue outcomes onto the shared error envelope. Keeps the catalogue
 * module from depending on platform internals beyond the public error contract.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogProviderUnavailableException.class)
    public ResponseEntity<ApiError> handleProviderUnavailable(HttpServletRequest request) {
        ApiError body = new ApiError(
                "CATALOG_PROVIDER_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Music catalogue is currently unavailable.",
                Map.of(),
                correlationId(request),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(CatalogResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
        ApiError body = new ApiError(
                "NOT_FOUND",
                HttpStatus.NOT_FOUND.value(),
                "The requested resource was not found.",
                Map.of(),
                correlationId(request),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value instanceof String id ? id : "unavailable";
    }
}
