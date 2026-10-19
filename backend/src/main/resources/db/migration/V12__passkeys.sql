-- Passkey backup (docs/design/passkey.md). A passkey is a WebAuthn credential: the phone keeps the private key
-- in its keychain, the server stores only the public key, so a database leak reveals nothing usable.

-- The WebAuthn user handle: 32 random bytes stored inside every passkey of this account. A discoverable
-- passkey sends it back on sign-in, which is how the server finds the account without an email.
-- NULL until the first passkey; UNIQUE allows any number of NULLs.
ALTER TABLE users ADD COLUMN passkey_handle BYTEA UNIQUE;

CREATE TABLE passkeys (
    id              BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- base64url of the authenticator's credential id; unique across all users, as WebAuthn requires
    credential_id   VARCHAR(1024) NOT NULL UNIQUE,
    public_key_cose BYTEA         NOT NULL,
    -- Synced passkeys (iCloud Keychain) always report 0; a hardware key counts up, which exposes clones
    signature_count BIGINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    last_used_at    TIMESTAMP
);

CREATE INDEX idx_passkeys_user ON passkeys(user_id);

-- A started registration or sign-in: the options (and so the challenge) the browser was given. Kept in the
-- database rather than in memory so a restart or a second instance does not break a ceremony in progress.
-- Single use: the row is deleted when the ceremony is finished, whether it succeeded or not.
CREATE TABLE webauthn_ceremonies (
    id           UUID        PRIMARY KEY,
    kind         VARCHAR(16) NOT NULL CHECK (kind IN ('REGISTER', 'LOGIN')),
    -- The signed-in user for REGISTER; NULL for LOGIN, where nobody is known yet
    user_id      BIGINT      REFERENCES users(id) ON DELETE CASCADE,
    request_json TEXT        NOT NULL,
    expires_at   TIMESTAMP   NOT NULL
);

CREATE INDEX idx_webauthn_ceremonies_expires ON webauthn_ceremonies(expires_at);

-- Extra passwords a device gets after a passkey sign-in, so it can sign in silently afterwards without
-- Face ID. Only the BCrypt hash is stored, like the main password. At most 10 per user (oldest dropped);
-- a password change deletes them all.
CREATE TABLE device_secrets (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    secret_hash VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_secrets_user ON device_secrets(user_id, created_at);
