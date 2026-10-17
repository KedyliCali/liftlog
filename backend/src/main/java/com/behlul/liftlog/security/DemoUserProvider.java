package com.behlul.liftlog.security;

import com.behlul.liftlog.repository.UserRepository;
import org.springframework.stereotype.Component;

/** MVP only: every request acts as the seeded demo user. */
@Component
public class DemoUserProvider implements CurrentUserProvider {

    static final String DEMO_EMAIL = "demo@liftlog.app";

    private final UserRepository userRepository;
    private volatile Long cachedId;

    public DemoUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Long currentUserId() {
        if (cachedId == null) {
            cachedId = userRepository.findByEmailIgnoreCase(DEMO_EMAIL)
                    .orElseThrow(() -> new IllegalStateException("Demo user is missing; check Flyway seed V3"))
                    .getId();
        }
        return cachedId;
    }
}
