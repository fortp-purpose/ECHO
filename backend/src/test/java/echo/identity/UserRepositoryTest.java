package echo.identity.domain;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.Instant;
import java.util.Optional;

@Testcontainers
@SpringBootTest(
    properties = {
        "spring.main.web-application-type=none"
    }
)
class UserRepositoryTest {

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

    @Autowired
    UserRepository userRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void saveAndRetrieveUser() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setHandle("testhandle");
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);
        assertNotNull(saved.getId(), "Saved user should have an ID");
        assertNotNull(saved.getCreatedAt(), "Created at should be set");

        Optional<User> found = userRepository.findByEmailNotDeleted("test@example.com");
        assertTrue(found.isPresent(), "User should be retrievable by email");
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void uniqueEmailConstraint() {
        User user = new User();
        user.setEmail("duplicate@test.com");
        user.setHandle("handle1");
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        User duplicate = new User();
        duplicate.setEmail("duplicate@test.com");
        duplicate.setHandle("handle2");
        duplicate.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        duplicate.setStatus("ACTIVE");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(duplicate),
            "Saving user with duplicate email should fail");
    }

    @Test
    void uniqueHandleConstraint() {
        User user = new User();
        user.setEmail("test2@example.com");
        user.setHandle("duplicatehandle");
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("ACTIVE");
        userRepository.save(user);

        User duplicate = new User();
        duplicate.setEmail("test3@example.com");
        duplicate.setHandle("duplicatehandle");
        duplicate.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        duplicate.setStatus("ACTIVE");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(duplicate),
            "Saving user with duplicate handle should fail");
    }

    @Test
    void softDeletedUserExcludedFromLookups() {
        User user = new User();
        user.setEmail("tosoftdel@test.com");
        user.setHandle("softdelhandle");
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmailNotDeleted("tosoftdel@test.com");
        assertTrue(found.isEmpty(), "Soft-deleted user should not be found by email lookup");
    }

    @Test
    void canFindActiveUserByEmail() {
        User user = new User();
        user.setEmail("activeuser@test.com");
        user.setHandle("activehandle");
        user.setPasswordHash("$2a$10$N.zQJjZBxQxQxQxQxQxQxu");
        user.setStatus("ACTIVE");
        user = userRepository.save(user);

        Optional<User> found = userRepository.findByEmailNotDeleted("activeuser@test.com");
        assertTrue(found.isPresent(), "Active user should be findable by email");
        assertEquals("activehandle", found.get().getHandle());
    }
}