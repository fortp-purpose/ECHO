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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {"spring.main.web-application-type=none"})
class CatalogServiceTest {

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("echo_dev")
            .withUsername("echo_user")
            .withPassword("echo_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Autowired
    CatalogService catalogService;

    @Autowired
    MusicProviderRepository providerRepository;

    @Autowired
    ArtistRepository artistRepository;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanCatalogue() {
        jdbcTemplate.execute("DELETE FROM tracks");
        jdbcTemplate.execute("DELETE FROM albums");
        jdbcTemplate.execute("DELETE FROM artists");
        jdbcTemplate.execute("DELETE FROM music_providers");
    }

    @Test
    void returnsTrackWhenProviderIsActive() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Artist artist = artistRepository.saveAndFlush(artist(provider, "a1", "Artist"));
        Album album = albumRepository.saveAndFlush(
                album(provider, artist, "al1", "Album", LocalDate.of(2020, 2, 2)));
        Track pending = track(provider, album, "t1", "Song");
        pending.setDurationMs(210_000);
        final Track track = trackRepository.saveAndFlush(pending);

        TrackView view = catalogService.getTrack(track.getId());

        assertEquals(track.getId(), view.id());
        assertEquals("Song", view.title());
        assertEquals(album.getId(), view.albumId());
        assertEquals(210_000, view.durationMs());
        assertTrue(view.available());
    }

    @Test
    void failsWhenNoActiveProviderExists() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("unverified", ProviderStatus.UNVERIFIED));
        Track track = trackRepository.saveAndFlush(track(provider, null, "t1", "Song"));

        assertThrows(CatalogProviderUnavailableException.class,
                () -> catalogService.getTrack(track.getId()),
                "no active provider means the catalogue is unavailable");
    }

    @Test
    void unknownTrackWithActiveProviderIsNotFoundNotUnavailable() {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));

        assertThrows(CatalogResourceNotFoundException.class,
                () -> catalogService.getTrack(UUID.randomUUID()),
                "an unknown id while a provider is active is a 404, not a 503");
    }

    @Test
    void unavailableTrackFailsWithProviderUnavailable() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Track pending = track(provider, null, "t1", "Song");
        pending.setAvailable(false);
        final Track track = trackRepository.saveAndFlush(pending);

        assertThrows(CatalogProviderUnavailableException.class,
                () -> catalogService.getTrack(track.getId()));
    }

    @Test
    void trackFromSuspendedProviderIsUnavailableEvenWhenAnotherIsActive() {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        MusicProvider suspended = providerRepository.saveAndFlush(
                provider("suspended", ProviderStatus.SUSPENDED));
        Track track = trackRepository.saveAndFlush(track(suspended, null, "t1", "Song"));

        assertThrows(CatalogProviderUnavailableException.class,
                () -> catalogService.getTrack(track.getId()),
                "the serving provider's status governs the resource");
    }

    @Test
    void returnsArtistAndAlbumViews() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Artist artist = artistRepository.saveAndFlush(artist(provider, "a1", "Artist"));
        Album album = albumRepository.saveAndFlush(
                album(provider, artist, "al1", "Album", LocalDate.of(2021, 3, 3)));

        ArtistView artistView = catalogService.getArtist(artist.getId());
        AlbumView albumView = catalogService.getAlbum(album.getId());

        assertEquals("Artist", artistView.name());
        assertEquals("Album", albumView.title());
        assertEquals(artist.getId(), albumView.primaryArtistId());
        assertEquals(LocalDate.of(2021, 3, 3), albumView.releaseDate());
    }

    @Test
    void listsAlbumTracksOrderedByTitle() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Album album = albumRepository.saveAndFlush(
                album(provider, null, "al1", "Album", LocalDate.of(2020, 1, 1)));
        trackRepository.saveAndFlush(track(provider, album, "t2", "Zebra"));
        trackRepository.saveAndFlush(track(provider, album, "t1", "Apple"));

        List<TrackView> tracks = catalogService.getAlbumTracks(album.getId());

        assertEquals(2, tracks.size());
        assertEquals("Apple", tracks.get(0).title());
        assertEquals("Zebra", tracks.get(1).title());
    }

    @Test
    void unknownAlbumWithActiveProviderIsNotFound() {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));

        assertThrows(CatalogResourceNotFoundException.class,
                () -> catalogService.getAlbum(UUID.randomUUID()));
    }

    @Test
    void albumTracksAreEmptyWhenAlbumHasNoTracks() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Album album = albumRepository.saveAndFlush(
                album(provider, null, "al1", "Empty", LocalDate.of(2020, 1, 1)));

        List<TrackView> tracks = catalogService.getAlbumTracks(album.getId());

        assertTrue(tracks.isEmpty());
        assertFalse(tracks.iterator().hasNext());
    }

    @Test
    void albumTracksExcludeUnavailableTracks() {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Album album = albumRepository.saveAndFlush(
                album(provider, null, "al1", "Album", LocalDate.of(2020, 1, 1)));
        trackRepository.saveAndFlush(track(provider, album, "t1", "Available"));
        Track withdrawn = track(provider, album, "t2", "Withdrawn");
        withdrawn.setAvailable(false);
        trackRepository.saveAndFlush(withdrawn);

        List<TrackView> tracks = catalogService.getAlbumTracks(album.getId());

        assertEquals(1, tracks.size(), "a withdrawn track must not be listed");
        assertEquals("Available", tracks.get(0).title());
    }

    @Test
    void albumTracksExcludeTracksFromNonActiveProvider() {
        MusicProvider active = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        MusicProvider suspended = providerRepository.saveAndFlush(
                provider("suspended", ProviderStatus.SUSPENDED));
        Album album = albumRepository.saveAndFlush(
                album(active, null, "al1", "Album", LocalDate.of(2020, 1, 1)));
        trackRepository.saveAndFlush(track(active, album, "t1", "Servable"));
        trackRepository.saveAndFlush(track(suspended, album, "t2", "NotServable"));

        List<TrackView> tracks = catalogService.getAlbumTracks(album.getId());

        assertEquals(1, tracks.size(), "a track from a non-active provider must not be listed");
        assertEquals("Servable", tracks.get(0).title());
    }

    @Test
    void albumTracksUnavailableWhenNoActiveProvider() {
        MusicProvider unverified = providerRepository.saveAndFlush(
                provider("unverified", ProviderStatus.UNVERIFIED));
        Album album = albumRepository.saveAndFlush(
                album(unverified, null, "al1", "Album", LocalDate.of(2020, 1, 1)));

        assertThrows(CatalogProviderUnavailableException.class,
                () -> catalogService.getAlbumTracks(album.getId()));
    }

    @Test
    void unknownAlbumTracksIsNotFoundWhenProviderActive() {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));

        assertThrows(CatalogResourceNotFoundException.class,
                () -> catalogService.getAlbumTracks(UUID.randomUUID()));
    }

    private MusicProvider provider(String code, ProviderStatus status) {
        MusicProvider provider = new MusicProvider();
        provider.setCode(code);
        provider.setName("Provider " + code);
        provider.setStatus(status);
        return provider;
    }

    private Artist artist(MusicProvider provider, String externalId, String name) {
        Artist artist = new Artist();
        artist.setProvider(provider);
        artist.setProviderExternalId(externalId);
        artist.setName(name);
        artist.setNormalisedName(name.toLowerCase());
        return artist;
    }

    private Album album(MusicProvider provider, Artist primaryArtist, String externalId,
                        String title, LocalDate releaseDate) {
        Album album = new Album();
        album.setProvider(provider);
        album.setPrimaryArtist(primaryArtist);
        album.setProviderExternalId(externalId);
        album.setTitle(title);
        album.setReleaseDate(releaseDate);
        return album;
    }

    private Track track(MusicProvider provider, Album album, String externalId, String title) {
        Track track = new Track();
        track.setProvider(provider);
        track.setAlbum(album);
        track.setProviderExternalId(externalId);
        track.setTitle(title);
        track.setNormalisedTitle(title.toLowerCase());
        return track;
    }
}
