package com.behlul.liftlog.security;

import com.behlul.liftlog.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TokenVersionValidatorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final TokenVersionValidator validator = new TokenVersionValidator(users);

    private static Jwt token(String subject, Object version) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").issuedAt(Instant.EPOCH);
        if (subject != null) {
            builder.subject(subject);
        }
        if (version != null) {
            builder.claim(JwtService.VERSION_CLAIM, version);
        }
        // Jwt needs at least one claim
        return builder.claim("email", "a@b.c").build();
    }

    @Test
    void tokensFromBeforeVersionsExistedCountAsVersionZero() {
        when(users.findTokenVersionById(7L)).thenReturn(Optional.of(0));
        assertThat(validator.validate(token("7", null)).hasErrors()).isFalse();
    }

    @Test
    void acceptsTheCurrentVersionAndRejectsOlderOnes() {
        when(users.findTokenVersionById(7L)).thenReturn(Optional.of(2));
        assertThat(validator.validate(token("7", 2)).hasErrors()).isFalse();
        assertThat(validator.validate(token("7", 1)).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokensOfUsersThatNoLongerExist() {
        when(users.findTokenVersionById(7L)).thenReturn(Optional.empty());
        assertThat(validator.validate(token("7", 0)).hasErrors()).isTrue();
    }

    @Test
    void rejectsMalformedSubjectsAndVersionsWithoutThrowing() {
        assertThat(validator.validate(token("not-a-number", 0)).hasErrors()).isTrue();
        assertThat(validator.validate(token(null, 0)).hasErrors()).isTrue();
        verifyNoInteractions(users);

        when(users.findTokenVersionById(7L)).thenReturn(Optional.of(0));
        assertThat(validator.validate(token("7", "zero")).hasErrors()).isTrue();
    }

    @Test
    void databaseErrorsAreNotTurnedIntoA401() {
        when(users.findTokenVersionById(7L)).thenThrow(new IllegalStateException("database down"));
        assertThatThrownBy(() -> validator.validate(token("7", 0))).isInstanceOf(IllegalStateException.class);
    }
}
