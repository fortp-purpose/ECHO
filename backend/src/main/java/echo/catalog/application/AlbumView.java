package echo.catalog.application;

import java.time.LocalDate;
import java.util.UUID;

public record AlbumView(
        UUID id,
        String title,
        UUID primaryArtistId,
        LocalDate releaseDate
) {
}
