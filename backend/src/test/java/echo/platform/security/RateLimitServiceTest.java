package echo.platform.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitServiceTest {
    @Test
    void canCreateService() {
        assertNotNull(new RateLimitService());
    }
}
