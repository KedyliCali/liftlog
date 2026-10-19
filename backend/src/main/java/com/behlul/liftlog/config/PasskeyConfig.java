package com.behlul.liftlog.config;

import com.behlul.liftlog.service.PasskeyCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.AttestationConveyancePreference;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The WebAuthn relying party (this server, as passkeys see it). Built from the CORS origins on purpose: the
 * web app is exactly the set of pages allowed to call the API, so no extra environment variable is needed.
 */
@Configuration
public class PasskeyConfig {

    private static final Logger log = LoggerFactory.getLogger(PasskeyConfig.class);

    static final String RP_NAME = "LiftLog";

    @Bean
    RelyingParty relyingParty(PasskeyCredentialRepository credentialRepository,
                              @Value("${liftlog.cors.allowed-origins}") List<String> allowedOrigins,
                              @Value("${liftlog.passkey.rp-id:}") String rpIdOverride) {
        Set<String> origins = new LinkedHashSet<>();
        for (String origin : allowedOrigins) {
            if (!origin.isBlank()) {
                origins.add(origin.trim());
            }
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException("liftlog.cors.allowed-origins must name at least one web origin");
        }
        String rpId = rpId(origins, rpIdOverride);
        // Not fatal: the API still serves those origins, only passkeys fail there, and saying so at startup
        // is far easier to act on than a vague browser error on a phone
        for (String origin : originsOutsideRpId(origins, rpId)) {
            log.warn("Passkeys will not work on {}: its host is neither the passkey RP id {} nor a subdomain of it",
                    origin, rpId);
        }
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name(RP_NAME).build())
                .credentialRepository(credentialRepository)
                .origins(origins)
                // No attestation: we do not restrict which authenticator makes the passkey, so asking for a
                // certificate chain would only add a privacy prompt on some platforms
                .attestationConveyancePreference(AttestationConveyancePreference.NONE)
                .build();
    }

    /** A browser only uses a passkey on the RP id's own host or its subdomains. */
    static List<String> originsOutsideRpId(Set<String> origins, String rpId) {
        String domain = rpId.toLowerCase(Locale.ROOT);
        return origins.stream()
                .filter(origin -> {
                    String host = URI.create(origin).getHost();
                    if (host == null) {
                        return true;
                    }
                    host = host.toLowerCase(Locale.ROOT);
                    return !host.equals(domain) && !host.endsWith("." + domain);
                })
                .toList();
    }

    /**
     * A passkey is bound to its RP id (a domain) forever, so it is the host of the web app unless set
     * explicitly, e.g. to a parent domain shared by several front ends.
     */
    static String rpId(Set<String> origins, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String host = URI.create(origins.iterator().next()).getHost();
        if (host == null) {
            throw new IllegalStateException("Cannot derive a passkey RP id from " + origins.iterator().next());
        }
        return host;
    }
}
