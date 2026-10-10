package echo.platform.api;

import java.time.Instant;
import java.util.Map;

/**
 * Stable public error contract. Never include stack traces or internal identifiers.
 */
public record ApiError(
        String code,
        int status,
        String message,
        Map<String, Object> details,
        String correlationId,
        Instant timestamp
) {
}
