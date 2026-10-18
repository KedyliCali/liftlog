package com.behlul.liftlog.security;

import com.behlul.liftlog.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects tokens issued before the user's last password change. JWTs are otherwise valid for 30 days with
 * no server state, so without this a lost phone would stay signed in after its recovery code was replaced.
 *
 * <p>Runs only after the signature check, so forged tokens never reach the database. Database errors are
 * deliberately not caught: they surface as a server error, because answering 401 would make every client
 * throw its token away and sign in again during an outage.
 */
final class TokenVersionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token is no longer valid", null);

    private final UserRepository userRepository;

    TokenVersionValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        if (userId == null) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        // Tokens issued before versions existed carry no claim; they count as version 0, so the release
        // that added this check signed nobody out
        Object claim = jwt.getClaims().get(JwtService.VERSION_CLAIM);
        int tokenVersion = claim == null ? 0 : claim instanceof Number n ? n.intValue() : -1;
        boolean current = userRepository.findTokenVersionById(userId)
                .map(version -> version == tokenVersion)
                .orElse(false);
        return current ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }

    private static Long parseUserId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
