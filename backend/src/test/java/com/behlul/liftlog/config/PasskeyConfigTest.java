package com.behlul.liftlog.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasskeyConfigTest {

    private static Set<String> origins(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Test
    void rpIdIsTheHostOfTheFirstOrigin() {
        assertThat(PasskeyConfig.rpId(origins("https://liftlog.example.com", "http://localhost:5173"), ""))
                .isEqualTo("liftlog.example.com");
        assertThat(PasskeyConfig.rpId(origins("http://localhost:5173"), null)).isEqualTo("localhost");
    }

    @Test
    void anExplicitRpIdWins() {
        assertThat(PasskeyConfig.rpId(origins("https://app.example.com"), " example.com ")).isEqualTo("example.com");
    }

    @Test
    void findsTheOriginsWherePasskeysCannotWork() {
        Set<String> all = origins("https://example.com", "https://app.example.com", "https://Example.com:8443",
                "https://other.dev", "https://notexample.com", "http://localhost:5173");
        assertThat(PasskeyConfig.originsOutsideRpId(all, "example.com"))
                .containsExactly("https://other.dev", "https://notexample.com", "http://localhost:5173");
        assertThat(PasskeyConfig.originsOutsideRpId(origins("http://localhost:5173"), "localhost")).isEmpty();
    }

    @Test
    void anOriginWithoutAHostIsAConfigurationError() {
        assertThatThrownBy(() -> PasskeyConfig.rpId(origins("not-a-url"), ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
