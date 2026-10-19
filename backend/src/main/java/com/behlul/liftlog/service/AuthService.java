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
import com.behlul.liftlog.dto.PasskeyDtos;
import com.behlul.liftlog.repository.DeviceSecretRepository;
import com.behlul.liftlog.repository.PasskeyRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import com.behlul.liftlog.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthService {

    /**
     * Each passkey sign-in adds one. Beyond this many the least recently used goes: a device that still signs
     * in keeps its secret, one that has not been seen for longest must use the passkey again.
     */
    static final int MAX_DEVICE_SECRETS = 20;
    static final String PASSKEY_SIGN_IN_FAILED = "Passkey sign-in failed";
    private static final Pattern DEVICE_SECRET = Pattern.compile(PasskeyDtos.DEVICE_SECRET_PATTERN);

    private final UserRepository userRepository;
    private final DeviceSecretRepository deviceSecretRepository;
    private final PasskeyRepository passkeyRepository;
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
                       PasskeyRepository passkeyRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       CurrentUserProvider currentUser, Clock clock) {
        this.userRepository = userRepository;
        this.deviceSecretRepository = deviceSecretRepository;
        this.passkeyRepository = passkeyRepository;
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

    /**
     * The password is the account's main password or one of its device secrets (see signInWithNewDeviceSecret).
     * Not read-only: a matching device secret records that it was used, which decides which secret is evicted.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            DeviceSecret secret = findMatchingDeviceSecret(user, request.password())
                    .orElseThrow(InvalidCredentialsException::new);
            secret.markUsed(LocalDateTime.now(clock));
        }
        return toAuthResponse(user);
    }

    /**
     * Finishes a passkey sign-in: stores the device's fresh secret as an extra password and returns a token.
     * From then on the device signs in silently with that secret, like a device that never lost its data, so
     * Face ID is needed only once per device.
     */
    @Transactional
    public AuthResponse signInWithNewDeviceSecret(Long userId, Long passkeyId, String deviceSecret) {
        // Row lock, shared with changePassword: a secret added while the password changes cannot slip in
        // after that change deleted all secrets
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(InvalidCredentialsException::new);
        // Checked again under the lock: a password change that committed after the signature was verified
        // deleted the passkey, and must win
        if (!passkeyRepository.existsById(passkeyId)) {
            throw new InvalidCredentialsException(PASSKEY_SIGN_IN_FAILED);
        }
        deviceSecretRepository.save(new DeviceSecret(userId, passwordEncoder.encode(deviceSecret), LocalDateTime.now(clock)));
        // Bounded, because a login may have to try each secret (one BCrypt check each)
        List<DeviceSecret> recentFirst = deviceSecretRepository.findByUserIdMostRecentlyUsedFirst(userId);
        if (recentFirst.size() > MAX_DEVICE_SECRETS) {
            deviceSecretRepository.deleteAll(recentFirst.subList(MAX_DEVICE_SECRETS, recentFirst.size()));
        }
        return toAuthResponse(user);
    }

    /**
     * Replaces the password ("New code") and cuts every other way in: the token version goes up, so all earlier
     * tokens fail on their next request; every device secret is deleted, so no device signs in silently; and
     * every passkey is deleted, so a lost phone (whose keychain still holds the passkey) cannot come back
     * either. The user registers a new passkey afterwards. The response carries a token with the new version
     * for the caller.
     */
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        Long id = currentUser.currentUserId();
        // Row lock: if two devices change the code at once, the second waits, then fails the check below
        // against the new hash, instead of both "succeeding" while only the last write survives
        User user = userRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        // A device that came back through a passkey knows only its device secret, not the main password
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())
                && findMatchingDeviceSecret(user, request.currentPassword()).isEmpty()) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        // Same transaction and row lock as the password change, so a passkey sign-in running at the same time
        // either finishes before (and its secret is deleted here) or waits and then finds no passkey
        deviceSecretRepository.deleteByUserId(id);
        passkeyRepository.deleteByUserId(id);
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
     * Callers check the main password first: it is what nearly every login uses, so those cost one BCrypt
     * check. Device secrets are tried only for a candidate shaped like one (43 base64url characters), so a
     * wrong ordinary password never costs up to 20 more checks. Most recently used first, because that device
     * is the most likely one to be signing in.
     */
    private Optional<DeviceSecret> findMatchingDeviceSecret(User user, String candidate) {
        if (!DEVICE_SECRET.matcher(candidate).matches()) {
            return Optional.empty();
        }
        return deviceSecretRepository.findByUserIdMostRecentlyUsedFirst(user.getId()).stream()
                .filter(secret -> passwordEncoder.matches(candidate, secret.getSecretHash()))
                .findFirst();
    }

    private AuthResponse toAuthResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
