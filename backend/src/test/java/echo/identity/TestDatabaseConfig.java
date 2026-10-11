package echo.identity.domain;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
    properties = {
        "spring.main.web-application-type=none"
    }
)
class TestDatabaseConfig {

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
    void entityManagerIsAvailable() {
        Query query = entityManager.createNativeQuery("SELECT 1");
        int result = ((Number) query.getSingleResult()).intValue();
        assertEquals(1, result, "EntityManager should be able to execute a simple query against the testcontainer PostgreSQL");
    }
}