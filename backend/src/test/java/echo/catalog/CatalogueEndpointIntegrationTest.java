package echo.catalog;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP contract test: real Spring context (controller, advice,
 * correlation filter, service, repositories) against a real PostgreSQL 16.
 * Proves the Phase 5.1 availability/error contract over the wire, not just in
 * isolated units.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogueEndpointIntegrationTest {

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
    MockMvc mockMvc;

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
    void returnsTrackViewOverHttpWithCorrelationIdHeader() throws Exception {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Album album = albumRepository.saveAndFlush(
                album(provider, null, "al1", "Album", LocalDate.of(2020, 1, 1)));
        Track track = trackRepository.saveAndFlush(track(provider, album, "t1", "Song"));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", track.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id",
                        matchesPattern("[0-9a-fA-F-]{36}")))
                .andExpect(jsonPath("$.id").value(track.getId().toString()))
                .andExpect(jsonPath("$.title").value("Song"))
                .andExpect(jsonPath("$.albumId").value(album.getId().toString()))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void unknownTrackReturns404EnvelopeOverHttp() throws Exception {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void noActiveProviderReturns503EnvelopeOverHttp() throws Exception {
        MusicProvider provider = providerRepository.saveAndFlush(
                provider("unverified", ProviderStatus.UNVERIFIED));
        Track track = trackRepository.saveAndFlush(track(provider, null, "t1", "Song"));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", track.getId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Music catalogue is currently unavailable."));
    }

    @Test
    void trackFromSuspendedProviderReturns503EvenWhenAnotherIsActive() throws Exception {
        providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        MusicProvider suspended = providerRepository.saveAndFlush(
                provider("suspended", ProviderStatus.SUSPENDED));
        Track track = trackRepository.saveAndFlush(track(suspended, null, "t1", "Song"));

        mockMvc.perform(get("/api/v1/catalog/tracks/{id}", track.getId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CATALOG_PROVIDER_UNAVAILABLE"));
    }

    @Test
    void albumTracksOverHttpExcludeWithdrawnTracks() throws Exception {
        MusicProvider provider = providerRepository.saveAndFlush(provider("active", ProviderStatus.ACTIVE));
        Album album = albumRepository.saveAndFlush(
                album(provider, null, "al1", "Album", LocalDate.of(2020, 1, 1)));
        trackRepository.saveAndFlush(track(provider, album, "t1", "Available"));
        Track withdrawn = track(provider, album, "t2", "Withdrawn");
        withdrawn.setAvailable(false);
        trackRepository.saveAndFlush(withdrawn);

        mockMvc.perform(get("/api/v1/catalog/albums/{id}/tracks", album.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Available"));
    }

    @Test
    void malformedIdReturns400EnvelopeOverHttp() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/tracks/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));
    }

    private MusicProvider provider(String code, ProviderStatus status) {
        MusicProvider provider = new MusicProvider();
        provider.setCode(code);
        provider.setName("Provider " + code);
        provider.setStatus(status);
        return provider;
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
