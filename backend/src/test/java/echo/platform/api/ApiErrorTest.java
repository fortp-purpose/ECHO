package echo.platform.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiErrorTest {

    @Test
    void exposesStableContractFieldsWithoutRequiringFrameworkStartup() {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        ApiError error = new ApiError(
                "CATALOG_PROVIDER_UNAVAILABLE",
                503,
                "Music discovery is temporarily unavailable.",
                Map.of("retryable", true),
                "2a7d8a31-9a0c-4baf-bc5c-8f8e2a8b2e11",
                timestamp
        );

        assertEquals("CATALOG_PROVIDER_UNAVAILABLE", error.code());
        assertEquals(503, error.status());
        assertEquals("Music discovery is temporarily unavailable.", error.message());
        assertEquals(true, error.details().get("retryable"));
        assertEquals("2a7d8a31-9a0c-4baf-bc5c-8f8e2a8b2e11", error.correlationId());
        assertEquals(timestamp, error.timestamp());
    }
}
