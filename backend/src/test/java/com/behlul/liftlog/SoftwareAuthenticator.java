package com.behlul.liftlog;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * A passkey authenticator in software, standing in for the phone in tests: an EC P-256 key pair, attestation
 * "none", and assertions signed exactly as a real authenticator signs them. It answers the server's real
 * options, so the tests drive the same verification code a browser would.
 */
final class SoftwareAuthenticator {

    static final String ORIGIN = "http://localhost:5173";
    static final String RP_ID = "localhost";

    // authenticatorData flags (WebAuthn §6.1)
    private static final int USER_PRESENT = 0x01;
    private static final int USER_VERIFIED = 0x04;
    private static final int BACKUP_ELIGIBLE = 0x08;
    private static final int BACKED_UP = 0x10;
    private static final int ATTESTED_CREDENTIAL_DATA = 0x40;

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private final JsonMapper json;
    private final KeyPair keyPair;
    private final byte[] credentialId = new byte[16];
    /** The user handle from the creation options; a real passkey stores it and returns it on sign-in. */
    private byte[] userHandle;

    // What a misbehaving or phishing client could change; the defaults are a well-behaved phone on the web app
    private String origin = ORIGIN;
    private String rpIdForHash = RP_ID;
    private boolean userVerified = true;

    SoftwareAuthenticator(JsonMapper json) {
        this.json = json;
        this.keyPair = newKeyPair();
        new SecureRandom().nextBytes(credentialId);
    }

    String credentialIdBase64Url() {
        return B64URL.encodeToString(credentialId);
    }

    byte[] userHandle() {
        return userHandle;
    }

    /** A page on another origin (e.g. a phishing site) relaying the server's options. */
    SoftwareAuthenticator usingOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    /** An authenticator answering for another site (rpIdHash of a different RP id). */
    SoftwareAuthenticator usingRpId(String rpId) {
        this.rpIdForHash = rpId;
        return this;
    }

    /** Clears the UV flag: the user only tapped, no Face ID or device PIN. */
    SoftwareAuthenticator withoutUserVerification() {
        this.userVerified = false;
        return this;
    }

    /** Back to a well-behaved phone. */
    SoftwareAuthenticator behaving() {
        origin = ORIGIN;
        rpIdForHash = RP_ID;
        userVerified = true;
        return this;
    }

    /** Answers navigator.credentials.create({publicKey}) and returns the credential as the web app sends it. */
    JsonNode create(JsonNode publicKey) {
        userHandle = B64URL_DECODER.decode(publicKey.get("user").get("id").asString());
        byte[] clientData = clientData("webauthn.create", publicKey.get("challenge").asString());
        byte[] authData = concat(rpIdHash(), new byte[]{(byte) (USER_PRESENT | uvFlag() | BACKUP_ELIGIBLE
                        | BACKED_UP | ATTESTED_CREDENTIAL_DATA)}, counter(),
                new byte[16], // AAGUID: all zero, as for attestation "none"
                ByteBuffer.allocate(2).putShort((short) credentialId.length).array(), credentialId, coseKey());
        ObjectNode response = json.createObjectNode()
                .put("clientDataJSON", B64URL.encodeToString(clientData))
                .put("attestationObject", B64URL.encodeToString(attestationObject(authData)));
        response.putArray("transports").add("internal").add("hybrid");
        return credential(response);
    }

    /** Answers navigator.credentials.get({publicKey}) with this passkey and its own user handle. */
    JsonNode get(JsonNode publicKey) {
        return get(publicKey, userHandle, keyPair.getPrivate());
    }

    /** A genuine signature, but presented with another account's user handle. */
    JsonNode getClaimingUserHandle(JsonNode publicKey, byte[] handle) {
        return get(publicKey, handle, keyPair.getPrivate());
    }

    /** Everything right except the signature, which comes from a key the server has never seen. */
    JsonNode getWithForgedSignature(JsonNode publicKey) {
        return get(publicKey, userHandle, newKeyPair().getPrivate());
    }

    private JsonNode get(JsonNode publicKey, byte[] handle, PrivateKey signingKey) {
        byte[] clientData = clientData("webauthn.get", publicKey.get("challenge").asString());
        byte[] authData = concat(rpIdHash(),
                new byte[]{(byte) (USER_PRESENT | uvFlag() | BACKUP_ELIGIBLE | BACKED_UP)}, counter());
        byte[] signature = sign(signingKey, concat(authData, sha256(clientData)));
        ObjectNode response = json.createObjectNode()
                .put("clientDataJSON", B64URL.encodeToString(clientData))
                .put("authenticatorData", B64URL.encodeToString(authData))
                .put("signature", B64URL.encodeToString(signature))
                .put("userHandle", B64URL.encodeToString(handle));
        return credential(response);
    }

    private JsonNode credential(ObjectNode response) {
        ObjectNode credential = json.createObjectNode()
                .put("id", credentialIdBase64Url())
                .put("rawId", credentialIdBase64Url())
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform");
        credential.set("response", response);
        credential.putObject("clientExtensionResults");
        return credential;
    }

    private byte[] clientData(String type, String challenge) {
        ObjectNode clientData = json.createObjectNode()
                .put("type", type)
                .put("challenge", challenge)
                .put("origin", origin)
                .put("crossOrigin", false);
        return json.writeValueAsString(clientData).getBytes(StandardCharsets.UTF_8);
    }

    private int uvFlag() {
        return userVerified ? USER_VERIFIED : 0;
    }

    /** Synced passkeys (iCloud Keychain) always report a signature counter of 0. */
    private static byte[] counter() {
        return new byte[4];
    }

    /** The public key as a COSE_Key map: kty EC2 (2), alg ES256 (-7), crv P-256 (1), x, y. */
    private byte[] coseKey() {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator cbor = new CBORFactory().createGenerator(out)) {
                cbor.writeStartObject(5);
                cbor.writeFieldId(1);
                cbor.writeNumber(2);
                cbor.writeFieldId(3);
                cbor.writeNumber(-7);
                cbor.writeFieldId(-1);
                cbor.writeNumber(1);
                cbor.writeFieldId(-2);
                cbor.writeBinary(unsigned32(publicKey.getW().getAffineX()));
                cbor.writeFieldId(-3);
                cbor.writeBinary(unsigned32(publicKey.getW().getAffineY()));
                cbor.writeEndObject();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] attestationObject(byte[] authData) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (CBORGenerator cbor = new CBORFactory().createGenerator(out)) {
                cbor.writeStartObject(3);
                cbor.writeFieldName("fmt");
                cbor.writeString("none");
                cbor.writeFieldName("attStmt");
                cbor.writeStartObject(0);
                cbor.writeEndObject();
                cbor.writeFieldName("authData");
                cbor.writeBinary(authData);
                cbor.writeEndObject();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** BigInteger adds a sign byte or drops leading zeros; COSE wants exactly 32 bytes. */
    private static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    private byte[] rpIdHash() {
        return sha256(rpIdForHash.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sign(PrivateKey key, byte[] data) {
        try {
            // DER-encoded ECDSA, the format WebAuthn uses for ES256
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(key);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
