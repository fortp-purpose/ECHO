package echo.catalog.application;

import java.util.UUID;

public record ArtistView(
        UUID id,
        String name
) {
}
