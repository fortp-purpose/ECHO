package echo.catalog.application;

import java.util.UUID;

public record TrackView(
        UUID id,
        String title,
        UUID albumId,
        Integer durationMs,
        boolean available
) {
}
