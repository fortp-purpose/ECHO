package echo.identity.domain;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {"spring.main.web-application-type=none"})
class FlywayMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("echo_dev")
        .withUsername("echo_user")
        .withPassword("echo_password");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        registry.add("spring.datasource.url", () -> postgresql.getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgresql.getUsername());
        registry.add("spring.datasource.password", () -> postgresql.getPassword());
    }

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void migrationAppliesUsersTable() {
        Query query = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'users'");
        long count = (Long) query.getSingleResult();
        assertTrue(count > 0, "Flyway V1 migration should create the users table");
    }

    @Test
    void migrationHasRequiredColumns() {
        Query query = entityManager.createNativeQuery(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_NAME = 'users' ORDER BY ORDINAL_POSITION");
        @SuppressWarnings("unchecked")
        java.util.List<String> columns = (java.util.List<String>) query.getResultList();
        assertTrue(columns.contains("id"), "users table should have id column");
        assertTrue(columns.contains("email"), "users table should have email column");
        assertTrue(columns.contains("password_hash"), "users table should have password_hash column");
        assertTrue(columns.contains("handle"), "users table should have handle column");
        assertTrue(columns.contains("deleted_at"), "users table should have deleted_at column");
        assertTrue(columns.contains("status"), "users table should have status column");
        assertTrue(columns.contains("created_at"), "users table should have created_at column");
    }

    @Test
    void migrationHasUniqueConstraints() {
        Query query = entityManager.createNativeQuery(
            "SELECT constraint_name FROM information_schema.table_constraints WHERE table_name = 'users' AND constraint_type = 'UNIQUE'");
        @SuppressWarnings("unchecked")
        java.util.List<String> constraints = (java.util.List<String>) query.getResultList();
        assertTrue(constraints.size() >= 2, "users table should have at least 2 unique constraints");
        for (String constraint : constraints) {
            assertTrue(constraint.contains("email") || constraint.contains("handle"),
                "Unique constraint should be on email or handle: " + constraint);
        }
    }

    @Test
    void usersTableHasStatusDefaultActive() {
        Query query = entityManager.createNativeQuery(
            "SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS WHERE TABLE_NAME = 'users' AND COLUMN_NAME = 'status'");
        String defaultValue = (String) query.getSingleResult();
        assertNotNull(defaultValue, "status column should have a default value");
        assertTrue(defaultValue.contains("active"), "status column should default to 'active': " + defaultValue);
    }
}