package echo.library.application;

import java.time.Instant;
import java.util.UUID;

/** Read model for a library item; never exposes the entity directly. */
public record LibraryItemView(
        UUID id,
        UUID trackId,
        UUID albumId,
        String type,
        Instant createdAt
) {
}
