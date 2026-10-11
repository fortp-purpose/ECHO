package echo.library;

import echo.catalog.domain.Album;
import echo.catalog.domain.AlbumRepository;
import echo.catalog.domain.MusicProvider;
import echo.catalog.domain.MusicProviderRepository;
import echo.catalog.domain.ProviderStatus;
import echo.catalog.domain.Track;
import echo.catalog.domain.TrackRepository;
import echo.identity.domain.User;
import echo.identity.domain.UserRepository;
import echo.library.domain.LibraryItem;
import echo.library.domain.LibraryItemRepository;
import echo.library.domain.LibraryItemType;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.main.web-application-type=none"})
@Testcontainers
class LibraryRepositoryTest {

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
    LibraryItemRepository libraryItems;

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
    void persistsTrackItemAndRoundTripsType() {
        User user = user("round-trip");
        Track track = track("t1");

        libraryItems.saveAndFlush(LibraryItem.track(user.getId(), track.getId(), LibraryItemType.SAVED));

        List<LibraryItem> found = libraryItems.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertEquals(1, found.size());
        assertEquals(LibraryItemType.SAVED, found.get(0).getType());
        assertEquals(track.getId(), found.get(0).getTrackId());
        assertTrue(found.get(0).getCreatedAt() != null, "created_at should be set");
    }

    @Test
    void sameSubjectAndTypeIsRejected() {
        User user = user("dup");
        Track track = track("t1");
        libraryItems.saveAndFlush(LibraryItem.track(user.getId(), track.getId(), LibraryItemType.FAVORITE));

        assertThrows(DataIntegrityViolationException.class, () ->
                libraryItems.saveAndFlush(LibraryItem.track(user.getId(), track.getId(), LibraryItemType.FAVORITE)),
                "duplicate (user, track, type) must be rejected");
    }

    @Test
    void sameTrackMayBeBothSavedAndFavorite() {
        User user = user("both");
        Track track = track("t1");

        libraryItems.saveAndFlush(LibraryItem.track(user.getId(), track.getId(), LibraryItemType.SAVED));
        libraryItems.saveAndFlush(LibraryItem.track(user.getId(), track.getId(), LibraryItemType.FAVORITE));

        assertEquals(2, libraryItems.findByUserIdOrderByCreatedAtDesc(user.getId()).size());
    }

    @Test
    void albumFavoritesAreSupportedAndDeduplicated() {
        User user = user("album");
        Album album = album("al1");
        libraryItems.saveAndFlush(LibraryItem.album(user.getId(), album.getId(), LibraryItemType.FAVORITE));

        assertThrows(DataIntegrityViolationException.class, () ->
                libraryItems.saveAndFlush(LibraryItem.album(user.getId(), album.getId(), LibraryItemType.FAVORITE)),
                "duplicate (user, album, type) must be rejected");

        assertEquals(1, libraryItems.findByUserIdAndTypeOrderByCreatedAtDesc(
                user.getId(), LibraryItemType.FAVORITE).size());
    }

    @Test
    void subjectCheckRejectsRowsWithBothSubjects() {
        User user = user("both-subjects");
        Track track = track("t1");
        Album album = album("al1");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO library_items (user_id, track_id, album_id, type) VALUES (?, ?, ?, 'saved')",
                user.getId(), track.getId(), album.getId()),
                "setting both track_id and album_id must be rejected");
    }

    @Test
    void subjectCheckRejectsRowsWithNoSubject() {
        User user = user("no-subject");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO library_items (user_id, type) VALUES (?, 'saved')",
                user.getId()),
                "setting neither track_id nor album_id must be rejected");
    }

    @Test
    void typeCheckRejectsUnknownType() {
        User user = user("bad-type");
        Track track = track("t1");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO library_items (user_id, track_id, type) VALUES (?, ?, 'bogus')",
                user.getId(), track.getId()),
                "an unknown type must be rejected by the CHECK constraint");
    }

    @Test
    void foreignKeyToUserIsEnforced() {
        Track track = track("t1");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO library_items (user_id, track_id, type) VALUES (?, ?, 'saved')",
                UUID.randomUUID(), track.getId()),
                "a library item referencing an unknown user must be rejected");
    }

    @Test
    void foreignKeyToTrackIsEnforced() {
        User user = user("fk-track");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO library_items (user_id, track_id, type) VALUES (?, ?, 'saved')",
                user.getId(), UUID.randomUUID()),
                "a library item referencing an unknown track must be rejected");
    }

    @Test
    void listIsOrderedNewestFirst() {
        User user = user("order");
        Track first = track("t1");
        Track second = track("t2");
        UUID itemNew = UUID.randomUUID();
        UUID itemOld = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO library_items (id, user_id, track_id, type, created_at) VALUES (?, ?, ?, 'saved', ?)",
                itemOld, user.getId(), first.getId(), java.sql.Timestamp.from(java.time.Instant.parse("2020-01-01T00:00:00Z")));
        jdbcTemplate.update(
                "INSERT INTO library_items (id, user_id, track_id, type, created_at) VALUES (?, ?, ?, 'saved', ?)",
                itemNew, user.getId(), second.getId(), java.sql.Timestamp.from(java.time.Instant.parse("2024-01-01T00:00:00Z")));

        List<LibraryItem> ordered = libraryItems.findByUserIdOrderByCreatedAtDesc(user.getId());

        assertEquals(List.of(itemNew, itemOld), ordered.stream().map(LibraryItem::getId).toList());
    }

    @Test
    void findByIdAndUserIdScopesToOwner() {
        User owner = user("owner");
        User other = user("other");
        Track track = track("t1");
        LibraryItem item = libraryItems.saveAndFlush(
                LibraryItem.track(owner.getId(), track.getId(), LibraryItemType.SAVED));

        assertTrue(libraryItems.findByIdAndUserId(item.getId(), owner.getId()).isPresent());
        Optional<LibraryItem> notOwner = libraryItems.findByIdAndUserId(item.getId(), other.getId());
        assertTrue(notOwner.isEmpty(), "an item must not be visible to a non-owner");
    }

    private User user(String handle) {
        User user = new User();
        user.setEmail(handle + "@example.com");
        user.setHandle(handle);
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("active");
        return userRepository.saveAndFlush(user);
    }

    private MusicProvider provider() {
        MusicProvider provider = new MusicProvider();
        provider.setCode("lib-provider-" + UUID.randomUUID());
        provider.setName("Library Provider");
        provider.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.saveAndFlush(provider);
    }

    private Album album(String externalId) {
        MusicProvider provider = provider();
        Album album = new Album();
        album.setProvider(provider);
        album.setProviderExternalId(externalId);
        album.setTitle("Album " + externalId);
        album.setReleaseDate(LocalDate.of(2020, 1, 1));
        return albumRepository.saveAndFlush(album);
    }

    private Track track(String externalId) {
        MusicProvider provider = provider();
        Track track = new Track();
        track.setProvider(provider);
        track.setProviderExternalId(externalId);
        track.setTitle("Track " + externalId);
        track.setNormalisedTitle(externalId);
        return trackRepository.saveAndFlush(track);
    }
}
