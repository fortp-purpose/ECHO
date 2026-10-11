package echo.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtTokenServiceTest {
    @Test
    void jwtTokenServiceCanBeCreated() {
        assertNotNull(new JwtTokenService(new JwtProperties(), new KeyRotationService(new JwtProperties())));
    }
}
