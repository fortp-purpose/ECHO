package echo.catalog;

import echo.catalog.domain.Album;
import echo.catalog.domain.AlbumRepository;
import echo.catalog.domain.Artist;
import echo.catalog.domain.ArtistRepository;
import echo.catalog.domain.MusicProvider;
import echo.catalog.domain.MusicProviderRepository;
import echo.catalog.domain.ProviderCapabilities;
import echo.catalog.domain.ProviderStatus;
import echo.catalog.domain.Track;
import echo.catalog.domain.TrackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {"spring.main.web-application-type=none"})
class CatalogueRepositoryTest {

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
    void persistsProviderWithStatusAndCapabilities() {
        ProviderCapabilities capabilities = new ProviderCapabilities(
                true, true, true, true, false, true, false, 100, true, Set.of("genre", "mood"));
        MusicProvider provider = provider("audius-test", ProviderStatus.ACTIVE);
        provider.setCapabilities(capabilities);
        providerRepository.saveAndFlush(provider);

        Optional<MusicProvider> found = providerRepository.findByCode("audius-test");
        assertTrue(found.isPresent(), "provider should be retrievable by code");
        assertEquals(ProviderStatus.ACTIVE, found.get().getStatus());
        assertEquals(capabilities, found.get().getCapabilities(),
                "capabilities JSON should round-trip");
        assertNotNull(found.get().getCreatedAt(), "created_at should be set");
    }

    @Test
    void providerCodeIsUnique() {
        providerRepository.saveAndFlush(provider("dup-code", ProviderStatus.UNVERIFIED));

        MusicProvider duplicate = provider("dup-code", ProviderStatus.UNVERIFIED);
        assertThrows(DataIntegrityViolationException.class,
                () -> providerRepository.saveAndFlush(duplicate),
                "duplicate provider code should be rejected");
    }

    @Test
    void artistIsUniquePerProviderAndExternalId() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("artist-uniq", ProviderStatus.ACTIVE));

        artistRepository.saveAndFlush(artist(provider, "ext-1", "First"));

        Artist duplicate = artist(provider, "ext-1", "Second");
        assertThrows(DataIntegrityViolationException.class,
                () -> artistRepository.saveAndFlush(duplicate),
                "duplicate (provider, external id) artist should be rejected");
    }

    @Test
    void albumIsUniquePerProviderAndExternalId() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("album-uniq", ProviderStatus.ACTIVE));

        albumRepository.saveAndFlush(album(provider, null, "ext-1", "First", LocalDate.of(2020, 1, 1)));

        Album duplicate = album(provider, null, "ext-1", "Second", LocalDate.of(2021, 1, 1));
        assertThrows(DataIntegrityViolationException.class,
                () -> albumRepository.saveAndFlush(duplicate),
                "duplicate (provider, external id) album should be rejected");
    }

    @Test
    void trackIsUniquePerProviderAndExternalId() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("track-uniq", ProviderStatus.ACTIVE));

        trackRepository.saveAndFlush(track(provider, null, "ext-1", "First"));

        Track duplicate = track(provider, null, "ext-1", "Second");
        assertThrows(DataIntegrityViolationException.class,
                () -> trackRepository.saveAndFlush(duplicate),
                "duplicate (provider, external id) track should be rejected");
    }

    @Test
    void trackForeignKeyToProviderIsEnforced() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tracks (provider_id, provider_external_id, title, normalised_title) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), "fk-provider", "Orphan", "orphan"),
                "track referencing an unknown provider should be rejected");
    }

    @Test
    void trackForeignKeyToAlbumIsEnforced() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("fk-album", ProviderStatus.ACTIVE));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO tracks (provider_id, provider_external_id, title, normalised_title, album_id) "
                        + "VALUES (?, ?, ?, ?, ?)",
                provider.getId(), "fk-album-track", "Orphan", "orphan", UUID.randomUUID()),
                "track referencing an unknown album should be rejected");
    }

    @Test
    void trackAvailabilityDefaultsToTrueInDatabase() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("default-avail", ProviderStatus.ACTIVE));

        jdbcTemplate.update(
                "INSERT INTO tracks (provider_id, provider_external_id, title, normalised_title) "
                        + "VALUES (?, ?, ?, ?)",
                provider.getId(), "default-avail-track", "Song", "song");

        Boolean available = jdbcTemplate.queryForObject(
                "SELECT is_available FROM tracks WHERE provider_external_id = 'default-avail-track'",
                Boolean.class);
        assertEquals(Boolean.TRUE, available, "is_available should default to true");
    }

    @Test
    void findsTrackByProviderAndExternalIdWithAlbum() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("lookup", ProviderStatus.ACTIVE));
        Artist artist = artistRepository.saveAndFlush(artist(provider, "lookup-artist", "Lookup Artist"));
        Album album = albumRepository.saveAndFlush(
                album(provider, artist, "lookup-album", "Lookup Album", LocalDate.of(2022, 5, 1)));
        trackRepository.saveAndFlush(track(provider, album, "lookup-track", "Lookup Track"));

        Optional<Track> found = trackRepository
                .findByProviderIdAndProviderExternalId(provider.getId(), "lookup-track");
        assertTrue(found.isPresent(), "track should be found by provider + external id");
        assertEquals(album.getId(), found.get().getAlbum().getId());
    }

    @Test
    void listsAlbumsByArtistNewestFirst() {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("albums-order", ProviderStatus.ACTIVE));
        Artist artist = artistRepository.saveAndFlush(artist(provider, "order-artist", "Order Artist"));
        albumRepository.saveAndFlush(album(provider, artist, "old", "Old", LocalDate.of(2019, 1, 1)));
        albumRepository.saveAndFlush(album(provider, artist, "new", "New", LocalDate.of(2023, 1, 1)));

        List<Album> albums = albumRepository.findByPrimaryArtistIdOrderByReleaseDateDesc(artist.getId());
        assertEquals(2, albums.size());
        assertEquals("New", albums.get(0).getTitle(), "newest album should come first");
    }

    private MusicProvider provider(String code, ProviderStatus status) {
        MusicProvider provider = new MusicProvider();
        provider.setCode(code);
        provider.setName("Test Provider " + code);
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
