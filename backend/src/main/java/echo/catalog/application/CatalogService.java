package echo.catalog.application;

import echo.catalog.domain.Album;
import echo.catalog.domain.AlbumRepository;
import echo.catalog.domain.Artist;
import echo.catalog.domain.ArtistRepository;
import echo.catalog.domain.MusicProvider;
import echo.catalog.domain.MusicProviderRepository;
import echo.catalog.domain.ProviderStatus;
import echo.catalog.domain.Track;
import echo.catalog.domain.TrackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only catalogue access. The catalogue is a mirror of provider data, so a
 * read is served only while at least one verified provider is active. When none
 * is, every read fails with {@link CatalogProviderUnavailableException} - the
 * documented CATALOG_PROVIDER_UNAVAILABLE steady state.
 */
@Service
public class CatalogService {

    private final MusicProviderRepository providers;
    private final ArtistRepository artists;
    private final AlbumRepository albums;
    private final TrackRepository tracks;

    public CatalogService(
            MusicProviderRepository providers,
            ArtistRepository artists,
            AlbumRepository albums,
            TrackRepository tracks
    ) {
        this.providers = providers;
        this.artists = artists;
        this.albums = albums;
        this.tracks = tracks;
    }

    @Transactional(readOnly = true)
    public TrackView getTrack(UUID trackId) {
        requireProviderAvailable();
        Track track = tracks.findById(trackId)
                .orElseThrow(() -> new CatalogResourceNotFoundException("Track", trackId));
        requireServingProvider(track.getProvider(), track.isAvailable());
        return toView(track);
    }

    @Transactional(readOnly = true)
    public ArtistView getArtist(UUID artistId) {
        requireProviderAvailable();
        Artist artist = artists.findById(artistId)
                .orElseThrow(() -> new CatalogResourceNotFoundException("Artist", artistId));
        requireServingProvider(artist.getProvider(), true);
        return new ArtistView(artist.getId(), artist.getName());
    }

    @Transactional(readOnly = true)
    public AlbumView getAlbum(UUID albumId) {
        requireProviderAvailable();
        Album album = albums.findById(albumId)
                .orElseThrow(() -> new CatalogResourceNotFoundException("Album", albumId));
        requireServingProvider(album.getProvider(), true);
        return toView(album);
    }

    @Transactional(readOnly = true)
    public List<TrackView> getAlbumTracks(UUID albumId) {
        requireProviderAvailable();
        Album album = albums.findById(albumId)
                .orElseThrow(() -> new CatalogResourceNotFoundException("Album", albumId));
        requireServingProvider(album.getProvider(), true);
        return tracks.findByAlbumIdOrderByTitleAsc(albumId).stream()
                .filter(CatalogService::isServable)
                .map(CatalogService::toView)
                .toList();
    }

    private void requireProviderAvailable() {
        if (!providers.existsByStatus(ProviderStatus.ACTIVE)) {
            throw new CatalogProviderUnavailableException();
        }
    }

    private void requireServingProvider(MusicProvider provider, boolean resourceAvailable) {
        if (!resourceAvailable
                || provider == null
                || provider.getStatus() != ProviderStatus.ACTIVE) {
            throw new CatalogProviderUnavailableException();
        }
    }

    /**
     * A track may be listed only while it is available and its own serving
     * provider is active. Availability of the enclosing album is necessary but
     * not sufficient: a track can be withdrawn, or supplied by a different
     * provider whose status has since changed.
     */
    private static boolean isServable(Track track) {
        MusicProvider provider = track.getProvider();
        return track.isAvailable()
                && provider != null
                && provider.getStatus() == ProviderStatus.ACTIVE;
    }

    private static TrackView toView(Track track) {
        UUID albumId = track.getAlbum() == null ? null : track.getAlbum().getId();
        return new TrackView(
                track.getId(),
                track.getTitle(),
                albumId,
                track.getDurationMs(),
                track.isAvailable());
    }

    private static AlbumView toView(Album album) {
        UUID artistId = album.getPrimaryArtist() == null ? null : album.getPrimaryArtist().getId();
        return new AlbumView(album.getId(), album.getTitle(), artistId, album.getReleaseDate());
    }
}
