package echo.platform.security;

import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtProperties properties;
    private final KeyRotationService keyRotationService;

    public JwtTokenService(JwtProperties properties, KeyRotationService keyRotationService) {
        this.properties = properties;
        this.keyRotationService = keyRotationService;
    }
}
