package com.behlul.liftlog.service;

import com.behlul.liftlog.entity.Passkey;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.repository.PasskeyRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets the Yubico library read our passkeys table. Its "username" is the account email and its "user handle"
 * is users.passkey_handle, the random id stored inside each passkey.
 */
@Component
@Transactional(readOnly = true)
public class PasskeyCredentialRepository implements CredentialRepository {

    private final UserRepository userRepository;
    private final PasskeyRepository passkeyRepository;

    public PasskeyCredentialRepository(UserRepository userRepository, PasskeyRepository passkeyRepository) {
        this.userRepository = userRepository;
        this.passkeyRepository = passkeyRepository;
    }

    /** Becomes excludeCredentials, so a phone that already holds a passkey for this account is not asked twice. */
    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .map(user -> passkeyRepository.findByUserIdOrderByCreatedAtAscIdAsc(user.getId()).stream()
                        .map(passkey -> PublicKeyCredentialDescriptor.builder().id(credentialId(passkey)).build())
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .map(User::getPasskeyHandle)
                .map(ByteArray::new);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return userRepository.findByPasskeyHandle(userHandle.getBytes()).map(User::getEmail);
    }

    /**
     * Only a credential that belongs to the account named by the handle: a valid signature from one user's
     * passkey sent with another user's handle must not sign in as that other user.
     */
    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        Optional<User> owner = userRepository.findByPasskeyHandle(userHandle.getBytes());
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                .filter(passkey -> passkey.getUserId().equals(owner.get().getId()))
                .map(passkey -> toRegistered(passkey, owner.get().getPasskeyHandle()));
    }

    /** Used by registration to refuse a credential id that is already registered, for any user. */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeyRepository.findByCredentialId(credentialId.getBase64Url())
                .flatMap(passkey -> userRepository.findById(passkey.getUserId())
                        .map(user -> toRegistered(passkey, user.getPasskeyHandle())))
                .map(Set::of)
                .orElse(Set.of());
    }

    private static RegisteredCredential toRegistered(Passkey passkey, byte[] userHandle) {
        return RegisteredCredential.builder()
                .credentialId(credentialId(passkey))
                .userHandle(new ByteArray(userHandle))
                .publicKeyCose(new ByteArray(passkey.getPublicKeyCose()))
                .signatureCount(passkey.getSignatureCount())
                .build();
    }

    private static ByteArray credentialId(Passkey passkey) {
        try {
            return ByteArray.fromBase64Url(passkey.getCredentialId());
        } catch (com.yubico.webauthn.data.exception.Base64UrlException e) {
            // Only ever written from a ByteArray's own base64url form, so this means a corrupted row
            throw new IllegalStateException("Stored passkey id is not base64url: " + passkey.getId(), e);
        }
    }
}
