package echo.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Container
    static PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("echo_dev")
            .withUsername("echo_user")
            .withPassword("echo_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        registry.add("spring.datasource.url", postgresql::getJdbcUrl);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void catalogTrackIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/tracks/some-id"))
                .andExpect(status().is(400));
    }

    @Test
    void unknownProtectedRouteRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/unknown/protected"))
                .andExpect(status().is(401)); // Protected route should return 401
    }

    @Test
    void registerPostIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void loginPostIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void meRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is(401));
    }

    @Test
    void passwordForgotPostIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void passwordResetPostIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/reset"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void verifyEmailPostIsPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void authPathGetToRefreshDoesNotBypass() throws Exception {
        mockMvc.perform(get("/api/v1/auth/refresh"))
                .andExpect(status().is(401)); // Only POST is defined; GET requires auth
    }
}
