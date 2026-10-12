package echo.platform.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceConfigTest {

    private final DataSourceConfig config = new DataSourceConfig();

    @Test
    void convertToJdbcUrl_alreadyJdbcFormat_returnsUnchanged() {
        String jdbcUrl = "jdbc:postgresql://host:5432/db?sslmode=require";
        String result = config.convertToJdbcUrl(jdbcUrl, "user", "pass");
        assertThat(result).isEqualTo(jdbcUrl);
    }

    @Test
    void convertToJdbcUrl_renderPostgresFormat_convertsCorrectly() {
        String renderUrl = "postgres://user:pass@host.render.com:5432/mydb";
        String result = config.convertToJdbcUrl(renderUrl, "ignored", "ignored");
        assertThat(result).isEqualTo("jdbc:postgresql://host.render.com:5432/mydb");
    }

    @Test
    void convertToJdbcUrl_renderPostgresqlFormat_convertsCorrectly() {
        String renderUrl = "postgresql://user:pass@host.render.com:5432/mydb?sslmode=require";
        String result = config.convertToJdbcUrl(renderUrl, "ignored", "ignored");
        assertThat(result).isEqualTo("jdbc:postgresql://host.render.com:5432/mydb?sslmode=require");
    }

    @Test
    void convertToJdbcUrl_withoutPort_usesDefaultPort() {
        String renderUrl = "postgres://user:pass@host.render.com/mydb";
        String result = config.convertToJdbcUrl(renderUrl, "ignored", "ignored");
        assertThat(result).isEqualTo("jdbc:postgresql://host.render.com/mydb");
    }

    @Test
    void convertToJdbcUrl_withoutCredentials_usesProvidedCredentials() {
        String renderUrl = "postgres://host.render.com:5432/mydb";
        String result = config.convertToJdbcUrl(renderUrl, "myuser", "mypass");
        assertThat(result).isEqualTo("jdbc:postgresql://host.render.com:5432/mydb");
    }

    @Test
    void convertToJdbcUrl_withQueryParams_preservesParams() {
        String renderUrl = "postgres://user:pass@host:5432/db?sslmode=require&connect_timeout=10";
        String result = config.convertToJdbcUrl(renderUrl, "ignored", "ignored");
        assertThat(result).isEqualTo("jdbc:postgresql://host:5432/db?sslmode=require&connect_timeout=10");
    }

    @Test
    void convertToJdbcUrl_invalidFormat_throwsException() {
        String invalidUrl = "mysql://user:pass@host:3306/db";
        assertThatThrownBy(() -> config.convertToJdbcUrl(invalidUrl, "user", "pass"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported database URL format");
    }

    @Test
    void convertToJdbcUrl_nullUrl_throwsException() {
        assertThatThrownBy(() -> config.convertToJdbcUrl(null, "user", "pass"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void convertToJdbcUrl_emptyUrl_throwsException() {
        assertThatThrownBy(() -> config.convertToJdbcUrl("", "user", "pass"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
    }

    @Test
    void convertToJdbcUrl_blankUrl_throwsException() {
        assertThatThrownBy(() -> config.convertToJdbcUrl("   ", "user", "pass"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not configured");
    }
}