package com.behlul.liftlog.security;

import com.behlul.liftlog.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * Applies AuthRateLimiter to the anonymous endpoints that cost real work: password checks, account creation
 * and passkey ceremonies. One budget per client across all of them, so spreading requests over several
 * endpoints does not multiply it.
 *
 * <p>Deliberately not a Spring bean: Spring Boot would also register any Filter bean for every request,
 * outside the security chain. SecurityConfig creates it and places it in the chain.
 */
final class AuthRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/passkey/options", "/api/v1/auth/passkey");

    private final AuthRateLimiter limiter;
    private final JsonMapper jsonMapper;

    AuthRateLimitFilter(AuthRateLimiter limiter, JsonMapper jsonMapper) {
        this.limiter = limiter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (limiter.tryAcquire(clientIp(request))) {
            chain.doFilter(request, response);
            return;
        }
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                "Too many requests, try again in a minute", request.getRequestURI(), null);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }

    /**
     * Render's proxy puts the caller's address first in X-Forwarded-For, so without it every request would
     * come from the proxy and share one budget. Off Render the header could be made up by the client, which
     * only lets it pick its own bucket; the ceremony cap and BCrypt cost still bound the damage.
     */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}
