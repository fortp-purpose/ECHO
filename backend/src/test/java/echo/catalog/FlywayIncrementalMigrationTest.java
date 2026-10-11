package echo.catalog;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves V2 and V3 apply incrementally on top of an already-migrated V1 schema,
 * not only on an empty database (testing-strategy 6.1).
 */
@Testcontainers
class FlywayIncrementalMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("echo_dev")
            .withUsername("echo_user")
            .withPassword("echo_password");

    @Test
    void migrationsApplyIncrementallyFromV1() throws SQLException {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        DataSource dataSource = dataSource();

        Flyway.configure()
                .dataSource(dataSource)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        assertTrue(tableExists(dataSource, "users"), "V1 should create users");
        assertFalse(tableExists(dataSource, "music_providers"),
                "music_providers must not exist after V1 only");
        assertFalse(tableExists(dataSource, "tracks"),
                "tracks must not exist after V1 only");

        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();

        assertTrue(tableExists(dataSource, "music_providers"), "V2 should apply incrementally");
        assertTrue(tableExists(dataSource, "artists"), "V3 should apply incrementally");
        assertTrue(tableExists(dataSource, "albums"), "V3 should apply incrementally");
        assertTrue(tableExists(dataSource, "tracks"), "V3 should apply incrementally");
        assertTrue(tableExists(dataSource, "library_items"), "V4 should apply incrementally");

        assertEquals(List.of("1", "2", "3", "4"), appliedVersions(dataSource));
    }

    private DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgresql.getJdbcUrl());
        dataSource.setUser(postgresql.getUsername());
        dataSource.setPassword(postgresql.getPassword());
        return dataSource;
    }

    private boolean tableExists(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '"
                             + tableName + "'")) {
            resultSet.next();
            return resultSet.getLong(1) > 0;
        }
    }

    private List<String> appliedVersions(DataSource dataSource) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version FROM flyway_schema_history "
                             + "WHERE success = true AND version IS NOT NULL "
                             + "ORDER BY installed_rank")) {
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
        }
        return versions;
    }
}
