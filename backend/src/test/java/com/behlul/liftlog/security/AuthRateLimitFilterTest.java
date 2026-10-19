package com.behlul.liftlog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The 429 path, with a limit of 2 so it is reached quickly; integration tests run with a very high limit. */
class AuthRateLimitFilterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new AuthRateLimiter(2, Duration.ofMinutes(1), 100, Clock.systemUTC()), jsonMapper);

    private MockHttpServletResponse post(String path, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void the3rdRequestInAMinuteGets429WithTheUsualErrorBody() throws Exception {
        assertThat(post("/api/v1/auth/login", "10.0.0.1", null).getStatus()).isEqualTo(200);
        // One budget across the limited endpoints
        assertThat(post("/api/v1/auth/passkey/options", "10.0.0.1", null).getStatus()).isEqualTo(200);

        MockHttpServletResponse refused = post("/api/v1/auth/passkey", "10.0.0.1", null);
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("60");
        JsonNode body = jsonMapper.readTree(refused.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("error").asString()).isEqualTo("Too Many Requests");
        assertThat(body.get("path").asString()).isEqualTo("/api/v1/auth/passkey");
        assertThat(body.get("message").asString()).isNotBlank();

        assertThat(post("/api/v1/auth/register", "10.0.0.2", null).getStatus()).isEqualTo(200);
    }

    @Test
    void behindAProxyTheFirstForwardedAddressIsTheClient() throws Exception {
        // All requests arrive from the proxy's address; the callers differ only in X-Forwarded-For
        post("/api/v1/auth/login", "10.9.9.9", "203.0.113.5, 10.9.9.9");
        post("/api/v1/auth/login", "10.9.9.9", "203.0.113.5");
        assertThat(post("/api/v1/auth/login", "10.9.9.9", "203.0.113.5, 10.1.1.1").getStatus()).isEqualTo(429);
        assertThat(post("/api/v1/auth/login", "10.9.9.9", "198.51.100.7").getStatus()).isEqualTo(200);
    }

    @Test
    void otherEndpointsAndMethodsAreNotCounted() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(post("/api/v1/workouts", "10.0.0.3", null).getStatus()).isEqualTo(200);
            assertThat(post("/api/v1/profile/passkeys/options", "10.0.0.3", null).getStatus()).isEqualTo(200);
            MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login");
            preflight.setRemoteAddr("10.0.0.3");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(preflight, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(post("/api/v1/auth/login", "10.0.0.3", null).getStatus()).isEqualTo(200);
    }
}
