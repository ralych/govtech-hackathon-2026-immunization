package ch.bff.producer.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            var payload = parseJwtPayload(token);
            var role = getString(payload, "role");
            if (role == null) {
                log.warn("JWT has no 'role' claim");
                filterChain.doFilter(request, response);
                return;
            }

            var sub = getString(payload, "sub");
            var userId = getString(payload, "userId");
            var principal = new UserPrincipal(sub, userId, role);

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwtPayload(String token) throws IOException {
        var parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("JWT has fewer than 2 segments");
        }
        var decoded = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readValue(decoded, Map.class);
    }

    private static String getString(Map<String, Object> map, String key) {
        var value = map.get(key);
        return value instanceof String s ? s : null;
    }
}
