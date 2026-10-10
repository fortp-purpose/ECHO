package echo.platform.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Supplies a safe correlation ID for every request and returns it to the caller.
 * Only UUID-shaped client values are accepted; malformed values are replaced.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".id";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER_NAME);
        String correlationId = supplied != null && UUID_PATTERN.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();

        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        filterChain.doFilter(request, response);
    }
}
