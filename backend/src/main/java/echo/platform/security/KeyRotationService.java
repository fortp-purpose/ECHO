package echo.platform.security;

import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class KeyRotationService {
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId;

    public KeyRotationService(JwtProperties properties) {
        try {
            this.keyId = properties.getKeyId();
            this.privateKey = loadPrivateKey(properties.getPrivateKeyBase64());
            this.publicKey = loadPublicKey(properties.getPublicKeyBase64());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA keys from configuration", e);
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyId() {
        return keyId;
    }

    private RSAPrivateKey loadPrivateKey(String base64) throws Exception {
        if (base64 == null || base64.isBlank()) {
            // Generate keys for development - will be overridden by env in production
            return null; // Simplified for foundation; keys must be provided via env
        }
        byte[] decoded = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(String base64) throws Exception {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        byte[] decoded = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }
}
