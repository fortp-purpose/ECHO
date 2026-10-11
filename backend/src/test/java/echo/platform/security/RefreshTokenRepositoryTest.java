package echo.platform.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RefreshTokenRepositoryTest {
    @Test
    void canCreateRepository() {
        assertNotNull(new RefreshTokenRepository());
    }
}
