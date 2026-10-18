package com.behlul.liftlog.controller;

import com.behlul.liftlog.dto.ProfileDtos.ProfileResponse;
import com.behlul.liftlog.dto.ProfileDtos.UpdateProfileRequest;
import com.behlul.liftlog.entity.UserAvatar;
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

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get() {
        return profileService.getProfile();
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(request);
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
