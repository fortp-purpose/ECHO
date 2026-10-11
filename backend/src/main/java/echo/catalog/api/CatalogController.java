package echo.catalog.api;

import echo.catalog.application.AlbumView;
import echo.catalog.application.ArtistView;
import echo.catalog.application.CatalogService;
import echo.catalog.application.TrackView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only catalogue surface. Public catalogue reads (no authentication
 * required, per api-strategy 7.3); writes do not exist here.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/tracks/{trackId}")
    public TrackView getTrack(@PathVariable UUID trackId) {
        return catalogService.getTrack(trackId);
    }

    @GetMapping("/artists/{artistId}")
    public ArtistView getArtist(@PathVariable UUID artistId) {
        return catalogService.getArtist(artistId);
    }

    @GetMapping("/albums/{albumId}")
    public AlbumView getAlbum(@PathVariable UUID albumId) {
        return catalogService.getAlbum(albumId);
    }

    @GetMapping("/albums/{albumId}/tracks")
    public List<TrackView> getAlbumTracks(@PathVariable UUID albumId) {
        return catalogService.getAlbumTracks(albumId);
    }
}
