package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.AuthDtos.AuthResponse;
import com.behlul.liftlog.dto.AuthDtos.ChangePasswordRequest;
import com.behlul.liftlog.dto.AuthDtos.LoginRequest;
import com.behlul.liftlog.dto.AuthDtos.RegisterRequest;
import com.behlul.liftlog.dto.AuthDtos.UserResponse;
import com.behlul.liftlog.entity.DeviceSecret;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.exception.ConflictException;
import com.behlul.liftlog.exception.InvalidCredentialsException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.DeviceSecretRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import com.behlul.liftlog.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    /** Each passkey sign-in adds one; older devices beyond this many must use the passkey again. */
    static final int MAX_DEVICE_SECRETS = 10;
    /** Device secrets are random base64url strings this long (see PasskeyLoginRequest). */
    static final int MIN_DEVICE_SECRET_LENGTH = 32;
    /** BCrypt reads only the first 72 bytes, and Spring's encoder refuses anything longer. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final DeviceSecretRepository deviceSecretRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    /**
     * Compared against when the email is unknown, so a failed login costs the same BCrypt time
     * either way and response timing does not leak which emails are registered.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, DeviceSecretRepository deviceSecretRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, CurrentUserProvider currentUser,
                       Clock clock) {
        this.userRepository = userRepository;
        this.deviceSecretRepository = deviceSecretRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
        this.clock = clock;
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

    /** The password is the account's main password or one of its device secrets (see signInWithNewDeviceSecret). */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!matchesPasswordOrDeviceSecret(user, request.password())) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    /**
     * Finishes a passkey sign-in: stores the device's fresh secret as an extra password and returns a token.
     * From then on the device signs in silently with that secret, like a device that never lost its data, so
     * Face ID is needed only once per device.
     */
    @Transactional
    public AuthResponse signInWithNewDeviceSecret(Long userId, String deviceSecret) {
        // Row lock, shared with changePassword: a secret added while the password changes cannot slip in
        // after that change deleted all secrets
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(InvalidCredentialsException::new);
        deviceSecretRepository.save(new DeviceSecret(userId, passwordEncoder.encode(deviceSecret), LocalDateTime.now(clock)));
        // Bounded, because every login may have to try each secret (one BCrypt check each)
        List<DeviceSecret> newestFirst = deviceSecretRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (newestFirst.size() > MAX_DEVICE_SECRETS) {
            deviceSecretRepository.deleteAll(newestFirst.subList(MAX_DEVICE_SECRETS, newestFirst.size()));
        }
        return toAuthResponse(user);
    }

    /**
     * Replaces the password and signs out every other device: the token version goes up, so all earlier
     * tokens fail on their next request, and every device secret is deleted, so no device can silently sign
     * in again either. The response carries a token with the new version for the caller.
     */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        Long id = currentUser.currentUserId();
        // Row lock: if two devices change the code at once, the second waits, then fails the check below
        // against the new hash, instead of both "succeeding" while only the last write survives
        User user = userRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        // A device that came back through a passkey knows only its device secret, not the main password
        if (!matchesPasswordOrDeviceSecret(user, request.currentPassword())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        deviceSecretRepository.deleteByUserId(id);
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

    /**
     * The main password first: it is what nearly every login uses, so those cost one BCrypt check. Device
     * secrets are tried only for a candidate shaped like one, which keeps a wrong short password from costing
     * up to 11 checks.
     */
    private boolean matchesPasswordOrDeviceSecret(User user, String candidate) {
        if (passwordEncoder.matches(candidate, user.getPasswordHash())) {
            return true;
        }
        if (candidate.length() < MIN_DEVICE_SECRET_LENGTH
                || candidate.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return deviceSecretRepository.findByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .anyMatch(secret -> passwordEncoder.matches(candidate, secret.getSecretHash()));
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
