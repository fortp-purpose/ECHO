package echo.catalog;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(properties = {"spring.main.web-application-type=none"})
class CatalogueMigrationTest {

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

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void v2CreatesMusicProvidersTableWithColumns() {
        assertTrue(tableExists("music_providers"), "V2 should create music_providers");
        List<String> columns = columnsOf("music_providers");
        assertTrue(columns.containsAll(List.of(
                "id", "code", "name", "status", "capabilities", "created_at", "updated_at")),
                "music_providers columns: " + columns);
    }

    @Test
    void musicProvidersStatusHasDefaultAndCheckConstraint() {
        String defaultValue = (String) entityManager.createNativeQuery(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_name = 'music_providers' AND column_name = 'status'")
                .getSingleResult();
        assertTrue(defaultValue != null && defaultValue.contains("unverified"),
                "status should default to 'unverified': " + defaultValue);

        Number checks = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_name = 'music_providers' AND constraint_type = 'CHECK' "
                        + "AND constraint_name = 'chk_music_providers_status'")
                .getSingleResult();
        assertEquals(1L, checks.longValue(), "status should be guarded by a CHECK constraint");
    }

    @Test
    void v3CreatesCatalogueTables() {
        assertTrue(tableExists("artists"), "V3 should create artists");
        assertTrue(tableExists("albums"), "V3 should create albums");
        assertTrue(tableExists("tracks"), "V3 should create tracks");
    }

    @Test
    void catalogueTablesAreProviderNamespacedUnique() {
        assertTrue(uniqueConstraints("artists").contains("uq_artists_provider_external"));
        assertTrue(uniqueConstraints("albums").contains("uq_albums_provider_external"));
        assertTrue(uniqueConstraints("tracks").contains("uq_tracks_provider_external"));
    }

    @Test
    void tracksReferenceProviderAndAlbum() {
        List<String> foreignKeyColumns = foreignKeyColumns("tracks");
        assertTrue(foreignKeyColumns.contains("provider_id"),
                "tracks.provider_id should be a foreign key: " + foreignKeyColumns);
        assertTrue(foreignKeyColumns.contains("album_id"),
                "tracks.album_id should be a foreign key: " + foreignKeyColumns);
    }

    @Test
    void trackAvailabilityDefaultsToTrue() {
        String defaultValue = (String) entityManager.createNativeQuery(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_name = 'tracks' AND column_name = 'is_available'")
                .getSingleResult();
        assertTrue(defaultValue != null && defaultValue.contains("true"),
                "is_available should default to true: " + defaultValue);
    }

    @Test
    void pgvectorAndEmbeddingColumnAreAbsent() {
        Number extensions = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'").getSingleResult();
        assertEquals(0L, extensions.longValue(), "pgvector must not be installed in Phase 5.1 (ADR-004)");

        Number embeddingColumns = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.columns WHERE column_name = 'embedding'")
                .getSingleResult();
        assertEquals(0L, embeddingColumns.longValue(), "no embedding column may exist before Phase 19");
    }

    private boolean tableExists(String tableName) {
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = :name")
                .setParameter("name", tableName)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> columnsOf(String tableName) {
        return entityManager.createNativeQuery(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = :name ORDER BY ordinal_position")
                .setParameter("name", tableName)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<String> uniqueConstraints(String tableName) {
        return entityManager.createNativeQuery(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = :name AND constraint_type = 'UNIQUE'")
                .setParameter("name", tableName)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<String> foreignKeyColumns(String tableName) {
        return entityManager.createNativeQuery(
                "SELECT kcu.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu "
                        + "ON tc.constraint_name = kcu.constraint_name "
                        + "AND tc.table_schema = kcu.table_schema "
                        + "WHERE tc.table_name = :name AND tc.constraint_type = 'FOREIGN KEY'")
                .setParameter("name", tableName)
                .getResultList();
    }
}
