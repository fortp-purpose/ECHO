package echo.library.application;

import echo.catalog.application.CatalogProviderUnavailableException;
import echo.catalog.application.CatalogResourceNotFoundException;
import echo.catalog.domain.Album;
import echo.catalog.domain.AlbumRepository;
import echo.catalog.domain.MusicProvider;
import echo.catalog.domain.MusicProviderRepository;
import echo.catalog.domain.ProviderStatus;
import echo.catalog.domain.Track;
import echo.catalog.domain.TrackRepository;
import echo.identity.domain.User;
import echo.identity.domain.UserRepository;
import echo.library.domain.LibraryItemType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.web-application-type=none"})
@Testcontainers
class LibraryServiceTest {

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
    LibraryService libraryService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    MusicProviderRepository providerRepository;

    @Autowired
    AlbumRepository albumRepository;

    @Autowired
    TrackRepository trackRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM library_items");
        jdbcTemplate.execute("DELETE FROM tracks");
        jdbcTemplate.execute("DELETE FROM albums");
        jdbcTemplate.execute("DELETE FROM music_providers");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Test
    void addTrackSavedReturnsView() {
        User user = user("saved");
        Track track = activeTrack();

        LibraryItemView view = libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED);

        assertEquals(track.getId(), view.trackId());
        assertEquals("saved", view.type());
        assertTrue(view.albumId() == null);
    }

    @Test
    void duplicateTrackItemIsRejected() {
        User user = user("dup");
        Track track = activeTrack();
        libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED);

        assertThrows(LibraryDuplicateItemException.class,
                () -> libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED));
    }

    @Test
    void sameTrackCanBeSavedAndFavorite() {
        User user = user("both");
        Track track = activeTrack();

        libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED);
        LibraryItemView favorite = libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.FAVORITE);

        assertEquals("favorite", favorite.type());
        assertEquals(2, libraryService.list(user.getId(), null).size());
    }

    @Test
    void addAlbumFavoriteReturnsView() {
        User user = user("album");
        Album album = activeAlbum();

        LibraryItemView view = libraryService.addAlbum(user.getId(), album.getId(), LibraryItemType.FAVORITE);

        assertEquals(album.getId(), view.albumId());
        assertEquals("favorite", view.type());
        assertTrue(view.trackId() == null);
    }

    @Test
    void unknownTrackIsNotFound() {
        User user = user("unknown-track");
        provider(ProviderStatus.ACTIVE);

        assertThrows(CatalogResourceNotFoundException.class,
                () -> libraryService.addTrack(user.getId(), UUID.randomUUID(), LibraryItemType.SAVED));
    }

    @Test
    void addTrackWithoutActiveProviderIsUnavailable() {
        User user = user("no-provider");
        MusicProvider unverified = provider(ProviderStatus.UNVERIFIED);
        Track track = track(unverified);

        assertThrows(CatalogProviderUnavailableException.class,
                () -> libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED));
    }

    @Test
    void addTrackForUnknownUserIsNotFound() {
        Track track = activeTrack();

        assertThrows(LibraryOwnerNotFoundException.class,
                () -> libraryService.addTrack(UUID.randomUUID(), track.getId(), LibraryItemType.SAVED));
    }

    @Test
    void listFiltersByType() {
        User user = user("filter");
        Track first = activeTrack();
        Track second = activeTrack();
        libraryService.addTrack(user.getId(), first.getId(), LibraryItemType.SAVED);
        libraryService.addTrack(user.getId(), second.getId(), LibraryItemType.FAVORITE);

        List<LibraryItemView> saved = libraryService.list(user.getId(), LibraryItemType.SAVED);
        List<LibraryItemView> favorites = libraryService.list(user.getId(), LibraryItemType.FAVORITE);

        assertEquals(1, saved.size());
        assertEquals("saved", saved.get(0).type());
        assertEquals(1, favorites.size());
        assertEquals("favorite", favorites.get(0).type());
        assertEquals(2, libraryService.list(user.getId(), null).size());
    }

    @Test
    void listForUnknownUserIsEmpty() {
        assertTrue(libraryService.list(UUID.randomUUID(), null).isEmpty());
    }

    @Test
    void removeOwnItemSucceeds() {
        User user = user("remove");
        Track track = activeTrack();
        LibraryItemView view = libraryService.addTrack(user.getId(), track.getId(), LibraryItemType.SAVED);

        libraryService.remove(user.getId(), view.id());

        assertTrue(libraryService.list(user.getId(), null).isEmpty());
    }

    @Test
    void removeAnotherUsersItemIsNotFound() {
        User owner = user("owner");
        User other = user("other");
        Track track = activeTrack();
        LibraryItemView view = libraryService.addTrack(owner.getId(), track.getId(), LibraryItemType.SAVED);

        assertThrows(LibraryItemNotFoundException.class,
                () -> libraryService.remove(other.getId(), view.id()));
        assertEquals(1, libraryService.list(owner.getId(), null).size(), "owner's item must survive");
    }

    @Test
    void libraryTypesMapToDistinctAiSignals() {
        assertEquals("SAVING", LibraryItemType.SAVED.preferenceSignal());
        assertEquals("LIKING", LibraryItemType.FAVORITE.preferenceSignal());
    }

    private User user(String handle) {
        User user = new User();
        user.setEmail(handle + "@example.com");
        user.setHandle(handle);
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("active");
        return userRepository.saveAndFlush(user);
    }

    private MusicProvider provider(ProviderStatus status) {
        MusicProvider provider = new MusicProvider();
        provider.setCode("svc-provider-" + UUID.randomUUID());
        provider.setName("Service Provider");
        provider.setStatus(status);
        return providerRepository.saveAndFlush(provider);
    }

    private Album activeAlbum() {
        MusicProvider provider = provider(ProviderStatus.ACTIVE);
        Album album = new Album();
        album.setProvider(provider);
        album.setProviderExternalId("al-" + UUID.randomUUID());
        album.setTitle("Album");
        album.setReleaseDate(LocalDate.of(2020, 1, 1));
        return albumRepository.saveAndFlush(album);
    }

    private Track activeTrack() {
        return track(provider(ProviderStatus.ACTIVE));
    }

    private Track track(MusicProvider provider) {
        Track track = new Track();
        track.setProvider(provider);
        track.setProviderExternalId("t-" + UUID.randomUUID());
        track.setTitle("Track");
        track.setNormalisedTitle("track");
        return trackRepository.saveAndFlush(track);
    }
}
