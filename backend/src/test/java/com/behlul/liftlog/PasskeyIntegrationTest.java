package com.behlul.liftlog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passkey backup end to end: a software authenticator answers the server's real WebAuthn options, so
 * registration and sign-in go through the real verification, security chain and database.
 * Same annotations as ApiIntegrationTest, so both share one Spring context and one PostgreSQL container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PasskeyIntegrationTest {

    private static final String PASSWORD = "first-password-0";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    @Test
    void registerListSignInWithPasskeyThenSilentlyWithTheDeviceSecret() throws Exception {
        String email = randomEmail();
        String token = register(email);
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);

        JsonNode creation = registrationOptions(token);
        assertExpiresInFiveMinutes(creation);
        JsonNode publicKey = creation.get("publicKey");
        assertThat(publicKey.get("rp").get("id").asString()).isEqualTo("localhost");
        assertThat(publicKey.get("rp").get("name").asString()).isEqualTo("LiftLog");
        assertThat(publicKey.get("user").get("name").asString()).isEqualTo(email);
        assertThat(Base64.getUrlDecoder().decode(publicKey.get("user").get("id").asString())).hasSize(32);
        assertThat(publicKey.get("authenticatorSelection").get("residentKey").asString()).isEqualTo("required");
        assertThat(publicKey.get("authenticatorSelection").get("userVerification").asString()).isEqualTo("required");
        assertThat(publicKey.get("attestation").asString()).isEqualTo("none");
        assertThat(publicKey.path("excludeCredentials").size()).isZero();

        authed(post("/api/v1/profile/passkeys"), token, registerBody(creation, phone.create(publicKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        authed(get("/api/v1/profile/passkeys"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastUsedAt").doesNotExist());

        // The next registration excludes the passkey the phone already has, and keeps the same user handle
        JsonNode again = registrationOptions(token).get("publicKey");
        assertThat(again.get("excludeCredentials").get(0).get("id").asString()).isEqualTo(phone.credentialIdBase64Url());
        assertThat(again.get("user").get("id").asString()).isEqualTo(publicKey.get("user").get("id").asString());

        // The phone lost its data: anonymous sign-in with the passkey and a fresh device secret
        JsonNode request = loginOptions();
        assertExpiresInFiveMinutes(request);
        assertThat(request.get("publicKey").path("allowCredentials").size()).isZero();
        assertThat(request.get("publicKey").get("userVerification").asString()).isEqualTo("required");
        assertThat(request.get("publicKey").get("rpId").asString()).isEqualTo("localhost");
        String deviceSecret = newDeviceSecret();
        MvcResult signedIn = passkeyLogin(request, phone.get(request.get("publicKey")), deviceSecret)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();
        String passkeyToken = read(signedIn).get("token").asString();
        me(passkeyToken).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        authed(get("/api/v1/profile/passkeys"), token, null)
                .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty());

        // From now on the device signs in silently with its secret; the original password still works too
        login(email, deviceSecret).andExpect(status().isOk());
        login(email, PASSWORD).andExpect(status().isOk());
        login(email, newDeviceSecret()).andExpect(status().isUnauthorized());
    }

    @Test
    void ceremoniesAreSingleUseAndBoundToTheirKindAndUser() throws Exception {
        String token = register(randomEmail());
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);
        JsonNode creation = registrationOptions(token);
        String credential = registerBody(creation, phone.create(creation.get("publicKey")));

        // Another user cannot finish this user's registration, and that attempt does not use the ceremony up
        String stranger = register(randomEmail());
        authed(post("/api/v1/profile/passkeys"), stranger, credential)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passkey ceremony is unknown, expired or already used"));

        authed(post("/api/v1/profile/passkeys"), token, credential).andExpect(status().isCreated());
        // Reused registration ceremony
        authed(post("/api/v1/profile/passkeys"), token, credential).andExpect(status().isBadRequest());

        JsonNode request = loginOptions();
        JsonNode assertion = phone.get(request.get("publicKey"));
        passkeyLogin(request, assertion, newDeviceSecret()).andExpect(status().isOk());
        // Reused sign-in ceremony: a captured response cannot be replayed
        passkeyLogin(request, assertion, newDeviceSecret()).andExpect(status().isBadRequest());

        // A sign-in ceremony cannot finish a registration, nor the other way round
        JsonNode loginCeremony = loginOptions();
        authed(post("/api/v1/profile/passkeys"), token, registerBody(loginCeremony, phone.create(creation.get("publicKey"))))
                .andExpect(status().isBadRequest());
        JsonNode registerCeremony = registrationOptions(token);
        passkeyLogin(registerCeremony, phone.get(registerCeremony.get("publicKey")), newDeviceSecret())
                .andExpect(status().isBadRequest());

        // Unknown ceremony
        ObjectNode unknown = jsonMapper.createObjectNode().put("ceremonyId", UUID.randomUUID().toString());
        unknown.set("publicKey", request.get("publicKey"));
        passkeyLogin(unknown, assertion, newDeviceSecret()).andExpect(status().isBadRequest());
    }

    @Test
    void expiredCeremonyIsRefused() throws Exception {
        String email = randomEmail();
        SoftwareAuthenticator phone = registeredPhone(email);
        JsonNode request = loginOptions();
        // From the app's clock, not the database's now(): the app stores these timestamps in UTC
        jdbc.update("update webauthn_ceremonies set expires_at = ? where id = ?",
                LocalDateTime.now(clock).minusSeconds(1), UUID.fromString(request.get("ceremonyId").asString()));

        passkeyLogin(request, phone.get(request.get("publicKey")), newDeviceSecret())
                .andExpect(status().isBadRequest());
        // Deleted, not just refused
        Integer left = jdbc.queryForObject("select count(*) from webauthn_ceremonies where id = ?", Integer.class,
                UUID.fromString(request.get("ceremonyId").asString()));
        assertThat(left).isZero();
    }

    @Test
    void wrongSignatureIsRejectedAndUsesTheCeremonyUp() throws Exception {
        SoftwareAuthenticator phone = registeredPhone(randomEmail());
        JsonNode request = loginOptions();
        JsonNode forged = phone.getWithForgedSignature(request.get("publicKey"));

        passkeyLogin(request, forged, newDeviceSecret())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Passkey sign-in failed"));
        // The failed attempt consumed the challenge: even the genuine signature is now too late
        passkeyLogin(request, phone.get(request.get("publicKey")), newDeviceSecret())
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPasskeyIsRejected() throws Exception {
        // A real account's user handle, but a passkey the server has never registered
        SoftwareAuthenticator registered = registeredPhone(randomEmail());
        SoftwareAuthenticator neverRegistered = new SoftwareAuthenticator(jsonMapper);
        JsonNode request = loginOptions();
        passkeyLogin(request, neverRegistered.getClaimingUserHandle(request.get("publicKey"), registered.userHandle()),
                newDeviceSecret())
                .andExpect(status().isUnauthorized());

        // A user handle that belongs to nobody
        byte[] handle = new byte[32];
        new SecureRandom().nextBytes(handle);
        JsonNode another = loginOptions();
        passkeyLogin(another, neverRegistered.getClaimingUserHandle(another.get("publicKey"), handle), newDeviceSecret())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSameCredentialCannotBeRegisteredTwice() throws Exception {
        String token = register(randomEmail());
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);
        JsonNode first = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token, registerBody(first, phone.create(first.get("publicKey"))))
                .andExpect(status().isCreated());

        // Same account again (a browser would honour excludeCredentials; this authenticator ignores it)
        JsonNode second = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token, registerBody(second, phone.create(second.get("publicKey"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passkey could not be verified"));

        // Another account trying to claim the same credential
        String other = register(randomEmail());
        JsonNode third = registrationOptions(other);
        authed(post("/api/v1/profile/passkeys"), other, registerBody(third, phone.create(third.get("publicKey"))))
                .andExpect(status().isBadRequest());

        authed(get("/api/v1/profile/passkeys"), token, null).andExpect(jsonPath("$.length()").value(1));
        authed(get("/api/v1/profile/passkeys"), other, null).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aPasskeyCannotSignInAsAnotherAccount() throws Exception {
        String victimEmail = randomEmail();
        SoftwareAuthenticator victimPhone = registeredPhone(victimEmail);
        String attackerEmail = randomEmail();
        SoftwareAuthenticator attackerPhone = registeredPhone(attackerEmail);

        // A valid signature from the attacker's own passkey, sent with the victim's user handle
        JsonNode request = loginOptions();
        passkeyLogin(request, attackerPhone.getClaimingUserHandle(request.get("publicKey"), victimPhone.userHandle()),
                newDeviceSecret())
                .andExpect(status().isUnauthorized());

        // Each passkey still signs in as its own account
        JsonNode own = loginOptions();
        passkeyLogin(own, attackerPhone.get(own.get("publicKey")), newDeviceSecret())
                .andExpect(jsonPath("$.user.email").value(attackerEmail));
    }

    @Test
    void newPasswordDeletesDeviceSecretsAndPasskeysAndSignsEveryoneOut() throws Exception {
        String email = randomEmail();
        String originalToken = register(email);
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);
        addPasskey(originalToken, phone);

        String deviceSecret = newDeviceSecret();
        JsonNode request = loginOptions();
        String passkeyToken = read(passkeyLogin(request, phone.get(request.get("publicKey")), deviceSecret)
                .andExpect(status().isOk()).andReturn()).get("token").asString();

        // The restored device knows only its device secret; that is enough as the current password
        String newToken = read(authed(put("/api/v1/profile/password"), passkeyToken,
                json("{'currentPassword':'%s','newPassword':'second-password-1'}", deviceSecret))
                .andExpect(status().isOk()).andReturn()).get("token").asString();

        login(email, deviceSecret).andExpect(status().isUnauthorized());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
        login(email, "second-password-1").andExpect(status().isOk());
        me(originalToken).andExpect(status().isUnauthorized());
        me(passkeyToken).andExpect(status().isUnauthorized());

        // "New code" cuts off everything: a lost phone still holding the passkey cannot come back with it
        JsonNode later = loginOptions();
        passkeyLogin(later, phone.get(later.get("publicKey")), newDeviceSecret())
                .andExpect(status().isUnauthorized());
        authed(get("/api/v1/profile/passkeys"), newToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aDeviceSecretWorksAsTheCurrentPasswordForANewCode() throws Exception {
        String email = randomEmail();
        SoftwareAuthenticator phone = registeredPhone(email);
        String deviceSecret = newDeviceSecret();
        JsonNode request = loginOptions();
        String token = read(passkeyLogin(request, phone.get(request.get("publicKey")), deviceSecret)
                .andExpect(status().isOk()).andReturn()).get("token").asString();

        authed(put("/api/v1/profile/password"), token, json("{'currentPassword':'%s','newPassword':'%s'}",
                newDeviceSecret(), "second-password-1"))
                .andExpect(status().isUnauthorized());
        authed(put("/api/v1/profile/password"), token, json("{'currentPassword':'%s','newPassword':'%s'}",
                deviceSecret, "second-password-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        login(email, "second-password-1").andExpect(status().isOk());
    }

    @Test
    void deviceSecretsAreEvictedLeastRecentlyUsedFirst() throws Exception {
        String email = randomEmail();
        SoftwareAuthenticator phone = registeredPhone(email);
        List<String> secrets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            secrets.add(passkeySignInWithNewSecret(phone));
        }
        // The oldest device keeps signing in, so it is now the most recently used
        login(email, secrets.get(0)).andExpect(status().isOk());
        // A secret whose last use is unknown counts as the oldest of all
        jdbc.update("update device_secrets set last_used_at = null where id = (select max(d.id) from device_secrets d"
                + " join users u on u.id = d.user_id where u.email = ?)", email);

        // 21st secret: the one with no last use goes
        secrets.add(passkeySignInWithNewSecret(phone));
        login(email, secrets.get(19)).andExpect(status().isUnauthorized());
        // 22nd: now the least recently used is secrets[1]; secrets[0] survives because it was used
        secrets.add(passkeySignInWithNewSecret(phone));
        login(email, secrets.get(1)).andExpect(status().isUnauthorized());
        login(email, secrets.get(0)).andExpect(status().isOk());
        login(email, secrets.get(2)).andExpect(status().isOk());
        login(email, secrets.get(21)).andExpect(status().isOk());
        Integer stored = jdbc.queryForObject(
                "select count(*) from device_secrets d join users u on u.id = d.user_id where u.email = ?",
                Integer.class, email);
        assertThat(stored).isEqualTo(20);
    }

    @Test
    void userVerificationIsRequired() throws Exception {
        String token = register(randomEmail());
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper).withoutUserVerification();
        JsonNode creation = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token, registerBody(creation, phone.create(creation.get("publicKey"))))
                .andExpect(status().isBadRequest());

        addPasskey(token, phone.behaving());
        JsonNode request = loginOptions();
        passkeyLogin(request, phone.withoutUserVerification().get(request.get("publicKey")), newDeviceSecret())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherOriginOrRpIdIsRejected() throws Exception {
        String token = register(randomEmail());
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);

        JsonNode creation = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token,
                registerBody(creation, phone.usingOrigin("https://evil.example").create(creation.get("publicKey"))))
                .andExpect(status().isBadRequest());
        JsonNode creation2 = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token,
                registerBody(creation2, phone.behaving().usingRpId("evil.example").create(creation2.get("publicKey"))))
                .andExpect(status().isBadRequest());

        addPasskey(token, phone.behaving());
        JsonNode request = loginOptions();
        passkeyLogin(request, phone.usingOrigin("https://evil.example").get(request.get("publicKey")), newDeviceSecret())
                .andExpect(status().isUnauthorized());
        JsonNode request2 = loginOptions();
        passkeyLogin(request2, phone.behaving().usingRpId("evil.example").get(request2.get("publicKey")),
                newDeviceSecret())
                .andExpect(status().isUnauthorized());

        // The same passkey, well behaved, still works
        JsonNode request3 = loginOptions();
        passkeyLogin(request3, phone.behaving().get(request3.get("publicKey")), newDeviceSecret())
                .andExpect(status().isOk());
    }

    @Test
    void validationAndAccess() throws Exception {
        // Registration needs a token; sign-in options do not
        mockMvc.perform(post("/api/v1/profile/passkeys/options")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/profile/passkeys")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/passkey/options")).andExpect(status().isOk());

        JsonNode request = loginOptions();
        ObjectNode body = jsonMapper.createObjectNode()
                .put("ceremonyId", request.get("ceremonyId").asString())
                .put("deviceSecret", "too-short");
        body.putObject("credential");
        mockMvc.perform(post("/api/v1/auth/passkey").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.deviceSecret").exists());
        // 43 characters, but not base64url; and base64url, but 44 characters
        for (String wrongShape : List.of("+".repeat(43), "a".repeat(44))) {
            body.put("deviceSecret", wrongShape);
            mockMvc.perform(post("/api/v1/auth/passkey").contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.deviceSecret").exists());
        }

        // A credential that is not a WebAuthn response at all
        body.put("deviceSecret", newDeviceSecret());
        mockMvc.perform(post("/api/v1/auth/passkey").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passkey credential is malformed"));
    }

    // --- helpers ---

    private String passkeySignInWithNewSecret(SoftwareAuthenticator phone) throws Exception {
        String secret = newDeviceSecret();
        JsonNode request = loginOptions();
        passkeyLogin(request, phone.get(request.get("publicKey")), secret).andExpect(status().isOk());
        return secret;
    }

    /** ISO-8601 in UTC ("...Z"), about five minutes ahead. */
    private static void assertExpiresInFiveMinutes(JsonNode options) {
        String expiresAt = options.get("expiresAt").asString();
        assertThat(expiresAt).endsWith("Z");
        assertThat(Instant.parse(expiresAt))
                .isBetween(Instant.now().plusSeconds(240), Instant.now().plusSeconds(301));
    }

    private SoftwareAuthenticator registeredPhone(String email) throws Exception {
        String token = register(email);
        SoftwareAuthenticator phone = new SoftwareAuthenticator(jsonMapper);
        addPasskey(token, phone);
        return phone;
    }

    private void addPasskey(String token, SoftwareAuthenticator phone) throws Exception {
        JsonNode creation = registrationOptions(token);
        authed(post("/api/v1/profile/passkeys"), token, registerBody(creation, phone.create(creation.get("publicKey"))))
                .andExpect(status().isCreated());
    }

    private JsonNode registrationOptions(String token) throws Exception {
        return read(authed(post("/api/v1/profile/passkeys/options"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyId").isNotEmpty())
                .andReturn());
    }

    private JsonNode loginOptions() throws Exception {
        return read(mockMvc.perform(post("/api/v1/auth/passkey/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyId").isNotEmpty())
                .andReturn());
    }

    private String registerBody(JsonNode options, JsonNode credential) {
        ObjectNode body = jsonMapper.createObjectNode().put("ceremonyId", options.get("ceremonyId").asString());
        body.set("credential", credential);
        return jsonMapper.writeValueAsString(body);
    }

    private ResultActions passkeyLogin(JsonNode options, JsonNode credential, String deviceSecret) throws Exception {
        ObjectNode body = jsonMapper.createObjectNode()
                .put("ceremonyId", options.get("ceremonyId").asString())
                .put("deviceSecret", deviceSecret);
        body.set("credential", credential);
        // Anonymous on purpose: the device asking has no token
        return mockMvc.perform(post("/api/v1/auth/passkey").contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("{'email':'%s','password':'%s'}", email, password)));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'%s','password':'%s'}", email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("token").asString();
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    /** What the phone sends: base64url of 32 random bytes, 43 characters. */
    private static String newDeviceSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String json(String template, Object... args) {
        return String.format(template, args).replace('\'', '"');
    }

    private static String randomEmail() {
        return "passkey-" + UUID.randomUUID() + "@example.com";
    }
}
