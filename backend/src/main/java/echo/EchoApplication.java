package echo;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import echo.platform.security.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class EchoApplication {

    public static void main(String[] args) {
        // JVM default timezone must be UTC: ISO-8601 UTC is the ECHO contract and PostgreSQL
        // rejects host-local timezones it does not recognise. See ADR-014. Do not remove.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(EchoApplication.class, args);
    }
}