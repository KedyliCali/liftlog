package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.AuthDtos.AuthResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyCreatedResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyLoginRequest;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyOptionsResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyResponse;
import com.behlul.liftlog.dto.PasskeyDtos.RegisterPasskeyRequest;
import com.behlul.liftlog.entity.Passkey;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.entity.WebAuthnCeremony.Kind;
import com.behlul.liftlog.exception.InvalidCredentialsException;
import com.behlul.liftlog.exception.InvalidPasskeyException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.PasskeyRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Passkey backup: register a passkey on a signed-in device, and sign in with it on a device that lost its
 * data. The cryptography (challenge, signature, origin and RP id checks) is the Yubico library's; this class
 * keeps the ceremonies, the passkeys table and the accounts in step.
 *
 * <p>The finish methods are deliberately not one transaction: the ceremony is consumed (and committed) first,
 * so it stays used up even when the verification that follows fails.
 */
@Service
public class PasskeyService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);

    /** WebAuthn allows up to 64 bytes; 32 random bytes cannot collide in practice and reveal nothing. */
    private static final int USER_HANDLE_BYTES = 32;

    private final RelyingParty relyingParty;
    private final PasskeyCeremonyStore ceremonies;
    private final PasskeyRepository passkeyRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final CurrentUserProvider currentUser;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasskeyService(RelyingParty relyingParty, PasskeyCeremonyStore ceremonies,
                          PasskeyRepository passkeyRepository, UserRepository userRepository,
                          AuthService authService, CurrentUserProvider currentUser, JsonMapper jsonMapper,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.relyingParty = relyingParty;
        this.ceremonies = ceremonies;
        this.passkeyRepository = passkeyRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.currentUser = currentUser;
        this.jsonMapper = jsonMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Creation options for the signed-in user. Resident key and user verification are required: the passkey
     * must be discoverable (sign-in later knows no email) and unlocked with Face ID, not just a tap.
     */
    public PasskeyOptionsResponse startRegistration() {
        Long userId = currentUser.currentUserId();
        User user = transaction.execute(status -> userWithPasskeyHandle(userId));
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(user.getEmail())
                        // Shown by the phone's passkey list; device accounts have no display name
                        .displayName(user.getDisplayName() != null ? user.getDisplayName() : "LiftLog")
                        .id(new ByteArray(user.getPasskeyHandle()))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .build());
        try {
            PasskeyCeremonyStore.Started ceremony = ceremonies.start(Kind.REGISTER, userId, options.toJson());
            return new PasskeyOptionsResponse(ceremony.id(), ceremony.expiresAt(),
                    publicKeyOf(options.toCredentialsCreateJson()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize passkey creation options", e);
        }
    }

    public PasskeyCreatedResponse finishRegistration(RegisterPasskeyRequest request) {
        Long userId = currentUser.currentUserId();
        String requestJson = ceremonies.consume(request.ceremonyId(), Kind.REGISTER, userId)
                .orElseThrow(() -> new InvalidPasskeyException(PasskeyCeremonyStore.UNKNOWN_CEREMONY));
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> credential;
        try {
            credential = PublicKeyCredential.parseRegistrationResponseJson(request.credential().toString());
        } catch (IOException | RuntimeException e) {
            throw new InvalidPasskeyException("Passkey credential is malformed");
        }
        RegistrationResult result;
        try {
            result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(requestJson))
                    .response(credential)
                    .build());
        } catch (RegistrationFailedException e) {
            // Also covers a credential id that is already registered (to this or another account)
            log.info("Passkey registration rejected for user {}: {}", userId, e.getMessage());
            throw new InvalidPasskeyException("Passkey could not be verified");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored passkey ceremony is unreadable", e);
        }
        Passkey passkey = new Passkey(userId, result.getKeyId().getId().getBase64Url(),
                result.getPublicKeyCose().getBytes(), result.getSignatureCount(), LocalDateTime.now(clock));
        try {
            return PasskeyCreatedResponse.from(passkeyRepository.save(passkey));
        } catch (DataIntegrityViolationException e) {
            // Two registrations of the same new credential raced past the library's duplicate check
            throw new InvalidPasskeyException("Passkey could not be verified");
        }
    }

    public List<PasskeyResponse> listPasskeys() {
        Long userId = currentUser.currentUserId();
        return passkeyRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(PasskeyResponse::from)
                .toList();
    }

    /**
     * Request options for an anonymous sign-in. No allow list: the phone offers whichever LiftLog passkey it
     * holds, and the user handle inside it names the account.
     */
    public PasskeyOptionsResponse startLogin() {
        AssertionRequest request = relyingParty.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build());
        try {
            PasskeyCeremonyStore.Started ceremony = ceremonies.start(Kind.LOGIN, null, request.toJson());
            return new PasskeyOptionsResponse(ceremony.id(), ceremony.expiresAt(),
                    publicKeyOf(request.toCredentialsGetJson()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize passkey request options", e);
        }
    }

    /**
     * Verifies the signed challenge, then signs the device in with its fresh device secret. Every failure of
     * the passkey itself is the same 401, so the response never says whether a credential exists.
     */
    public AuthResponse finishLogin(PasskeyLoginRequest request) {
        String requestJson = ceremonies.consume(request.ceremonyId(), Kind.LOGIN, null)
                .orElseThrow(() -> new InvalidPasskeyException(PasskeyCeremonyStore.UNKNOWN_CEREMONY));
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> credential;
        try {
            credential = PublicKeyCredential.parseAssertionResponseJson(request.credential().toString());
        } catch (IOException | RuntimeException e) {
            throw new InvalidPasskeyException("Passkey credential is malformed");
        }
        AssertionResult result;
        try {
            // Checks the signature with the stored public key, the challenge, origin, RP id, user verification,
            // and that the credential belongs to the account its user handle names
            result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(requestJson))
                    .response(credential)
                    .build());
        } catch (AssertionFailedException e) {
            log.info("Passkey sign-in rejected: {}", e.getMessage());
            throw new InvalidCredentialsException("Passkey sign-in failed");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored passkey ceremony is unreadable", e);
        }
        if (!result.isSuccess()) {
            throw new InvalidCredentialsException("Passkey sign-in failed");
        }
        Passkey passkey = passkeyRepository.findByCredentialId(result.getCredentialId().getBase64Url())
                .orElseThrow(() -> new InvalidCredentialsException("Passkey sign-in failed"));
        passkeyRepository.recordUse(passkey.getId(), result.getSignatureCount(), LocalDateTime.now(clock));
        return authService.signInWithNewDeviceSecret(passkey.getUserId(), passkey.getId(), request.deviceSecret());
    }

    /**
     * The user handle is made on the first registration and then kept: every passkey of the account carries
     * it. Row lock, so two devices starting their first registration at once cannot get different handles.
     */
    private User userWithPasskeyHandle(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.getPasskeyHandle() == null) {
            byte[] handle = new byte[USER_HANDLE_BYTES];
            random.nextBytes(handle);
            user.assignPasskeyHandle(handle);
        }
        return user;
    }

    /** The library wraps the options as {"publicKey": {...}}; the client gets the inner object. */
    private JsonNode publicKeyOf(String credentialsJson) {
        return jsonMapper.readTree(credentialsJson).get("publicKey");
    }
}
