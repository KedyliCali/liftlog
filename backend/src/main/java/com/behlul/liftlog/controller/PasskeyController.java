package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.AuthDtos.AuthResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyCreatedResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyLoginRequest;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyOptionsResponse;
import com.behlul.liftlog.dto.PasskeyDtos.PasskeyResponse;
import com.behlul.liftlog.dto.PasskeyDtos.RegisterPasskeyRequest;
import com.behlul.liftlog.service.PasskeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Passkey backup. Registering lives under /profile (a signed-in device backs its account up); signing in
 * lives under /auth and is anonymous, because the device asking has lost its account.
 */
@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;

    public PasskeyController(PasskeyService passkeyService) {
        this.passkeyService = passkeyService;
    }

    @PostMapping("/api/v1/profile/passkeys/options")
    public PasskeyOptionsResponse registrationOptions() {
        return passkeyService.startRegistration();
    }

    @PostMapping("/api/v1/profile/passkeys")
    @ResponseStatus(HttpStatus.CREATED)
    public PasskeyCreatedResponse register(@Valid @RequestBody RegisterPasskeyRequest request) {
        return passkeyService.finishRegistration(request);
    }

    @GetMapping("/api/v1/profile/passkeys")
    public List<PasskeyResponse> list() {
        return passkeyService.listPasskeys();
    }

    @PostMapping("/api/v1/auth/passkey/options")
    public PasskeyOptionsResponse loginOptions() {
        return passkeyService.startLogin();
    }

    @PostMapping("/api/v1/auth/passkey")
    public AuthResponse login(@Valid @RequestBody PasskeyLoginRequest request) {
        return passkeyService.finishLogin(request);
    }
}
