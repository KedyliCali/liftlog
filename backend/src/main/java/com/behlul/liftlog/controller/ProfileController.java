package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.AuthDtos.AuthResponse;
import com.behlul.liftlog.dto.AuthDtos.ChangePasswordRequest;
import com.behlul.liftlog.dto.ProfileDtos.ProfileResponse;
import com.behlul.liftlog.dto.ProfileDtos.UpdateProfileRequest;
import com.behlul.liftlog.entity.UserAvatar;
import com.behlul.liftlog.service.AuthService;
import com.behlul.liftlog.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final AuthService authService;

    public ProfileController(ProfileService profileService, AuthService authService) {
        this.profileService = profileService;
        this.authService = authService;
    }

    @GetMapping
    public ProfileResponse get() {
        return profileService.getProfile();
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(request);
    }

    /**
     * New password, which the web app presents as "New code". Lives under /profile rather than /auth because
     * the Android client strips the token from every /auth call except /me.
     */
    @PutMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(request);
    }

    /** The raw image is the request body; simpler for a mobile client than multipart. */
    @PutMapping(value = "/avatar", consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ProfileResponse uploadAvatar(@RequestBody byte[] image) {
        return profileService.replaceAvatar(image);
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar() {
        UserAvatar avatar = profileService.getAvatar();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.getContentType()))
                // private: the image belongs to one user, so shared caches must not keep it
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(avatar.getData());
    }

    @DeleteMapping("/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar() {
        profileService.deleteAvatar();
    }
}
