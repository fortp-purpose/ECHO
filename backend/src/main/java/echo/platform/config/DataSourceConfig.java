package echo.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.pool-max-size:5}") int maxPoolSize
    ) {
        String jdbcUrl = convertToJdbcUrl(url, username, password);
        String effectiveUsername = resolveUsername(url, username);
        String effectivePassword = resolvePassword(url, password);
        if (effectiveUsername.isBlank()) {
            throw new IllegalStateException("Database username is not configured");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(effectiveUsername);
        dataSource.setPassword(effectivePassword);
        dataSource.setMaximumPoolSize(Math.max(1, maxPoolSize));
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(10_000);
        dataSource.setMaxLifetime(600_000);

        return dataSource;
    }

    /**
     * Credentials travel inside Render's DATABASE_URL userinfo
     * ({@code postgres://user:pass@host/...}), so they must be read from the
     * URL first and only fall back to the separate username/password values.
     */
    static String resolveUsername(String url, String username) {
        String fromUri = userInfoPart(url, 0);
        if (fromUri != null && !fromUri.isBlank()) {
            return fromUri;
        }
        return username != null ? username : "";
    }

    static String resolvePassword(String url, String password) {
        String fromUri = userInfoPart(url, 1);
        if (fromUri != null) {
            return fromUri;
        }
        return password != null ? password : "";
    }

    private static String userInfoPart(String url, int index) {
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String userInfo = uri.getUserInfo();
            if (userInfo == null) {
                return null;
            }
            String[] parts = userInfo.split(":", 2);
            if (index >= parts.length) {
                return null;
            }
            return URLDecoder.decode(parts[index], StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert Render's PostgreSQL URL to JDBC format.
     *
     * Render provides URLs in formats like:
     * - postgres://user:password@host:port/database
     * - postgresql://user:password@host:port/database
     * - postgres://user:password@host:port/database?param=value
     *
     * Spring Boot JDBC requires: jdbc:postgresql://host:port/database?param=value
     */
    String convertToJdbcUrl(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Database URL is not configured");
        }

        // Already in JDBC format
        if (url.startsWith("jdbc:postgresql://")) {
            return url;
        }

        // Handle Render's postgres:// or postgresql:// format
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            try {
                URI uri = new URI(url);
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();

                // Extract database name from path (remove leading /)
                String database = path != null && path.startsWith("/") ? path.substring(1) : path;

                // Extract credentials from URI if not provided separately
                String user = uri.getUserInfo() != null ? uri.getUserInfo().split(":")[0] : username;
                String pass = uri.getUserInfo() != null && uri.getUserInfo().contains(":")
                    ? uri.getUserInfo().split(":")[1] : password;

                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://");
                jdbcUrl.append(host);
                if (port > 0) {
                    jdbcUrl.append(":").append(port);
                }
                if (database != null && !database.isBlank()) {
                    jdbcUrl.append("/").append(database);
                }
                if (query != null && !query.isBlank()) {
                    jdbcUrl.append("?").append(query);
                }

                return jdbcUrl.toString();
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Invalid PostgreSQL URL format: " + url, e);
            }
        }

        throw new IllegalStateException("Unsupported database URL format: " + url);
    }
}