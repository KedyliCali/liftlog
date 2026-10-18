package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.AuthDtos.AuthResponse;
import com.behlul.liftlog.dto.AuthDtos.ChangePasswordRequest;
import com.behlul.liftlog.dto.AuthDtos.LoginRequest;
import com.behlul.liftlog.dto.AuthDtos.RegisterRequest;
import com.behlul.liftlog.dto.AuthDtos.UserResponse;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.exception.InvalidCredentialsException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import com.behlul.liftlog.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUser;

    /**
     * Compared against when the email is unknown, so a failed login costs the same BCrypt time
     * either way and response timing does not leak which emails are registered.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       CurrentUserProvider currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? null : request.displayName().trim();
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()), displayName));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    /**
     * Replaces the password and signs out every other device: the token version goes up, so all earlier
     * tokens fail on their next request. The response carries a token with the new version for the caller.
     */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        Long id = currentUser.currentUserId();
        // Row lock: if two devices change the code at once, the second waits, then fails the check below
        // against the new hash, instead of both "succeeding" while only the last write survives
        User user = userRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        // Built from the updated entity, so the token already has the new version; it is written at commit
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        Long id = currentUser.currentUserId();
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
