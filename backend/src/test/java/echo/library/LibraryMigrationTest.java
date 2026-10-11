package echo.library;

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

@SpringBootTest(properties = {"spring.main.web-application-type=none"})
@Testcontainers
class LibraryMigrationTest {

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
    void v4CreatesLibraryItemsTableWithColumns() {
        assertTrue(tableExists("library_items"), "V4 should create library_items");
        List<String> columns = columnsOf("library_items");
        assertTrue(columns.containsAll(List.of(
                "id", "user_id", "track_id", "album_id", "type", "created_at")),
                "library_items columns: " + columns);
    }

    @Test
    void typeIsGuardedByCheckConstraint() {
        assertTrue(checkConstraints("library_items").contains("chk_library_items_type"),
                "type must be a constrained set (saved | favorite)");
    }

    @Test
    void subjectMutualExclusionIsGuardedByCheckConstraint() {
        assertTrue(checkConstraints("library_items").contains("chk_library_items_subject"),
                "exactly one of track_id/album_id must be set");
    }

    @Test
    void tracksAreUniquePerUserAndType() {
        List<String> unique = uniqueConstraints("library_items");
        assertTrue(unique.contains("uq_library_items_user_track_type"), unique.toString());
        assertTrue(unique.contains("uq_library_items_user_album_type"), unique.toString());
    }

    @Test
    void referencesUserTrackAndAlbum() {
        List<String> foreignKeyColumns = foreignKeyColumns("library_items");
        assertTrue(foreignKeyColumns.contains("user_id"), foreignKeyColumns.toString());
        assertTrue(foreignKeyColumns.contains("track_id"), foreignKeyColumns.toString());
        assertTrue(foreignKeyColumns.contains("album_id"), foreignKeyColumns.toString());
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
    private List<String> checkConstraints(String tableName) {
        return entityManager.createNativeQuery(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = :name AND constraint_type = 'CHECK'")
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
