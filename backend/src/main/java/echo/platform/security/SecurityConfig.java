package echo.platform.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/api/v1/catalog/**").permitAll()
                // Public auth endpoints only
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/register").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/password/forgot").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/password/reset").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                .requestMatchers("/api/v1/auth/**").authenticated()  // All other auth endpoints require auth
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"status\":401,\"message\":\"Unauthenticated\"}");
                })
            );

        return http.build();
    }
}