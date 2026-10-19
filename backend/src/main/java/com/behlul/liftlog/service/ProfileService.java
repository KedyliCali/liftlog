package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.ProfileDtos.ProfileResponse;
import com.behlul.liftlog.dto.ProfileDtos.UpdateProfileRequest;
import com.behlul.liftlog.entity.User;
import com.behlul.liftlog.entity.UserAvatar;
import com.behlul.liftlog.exception.InvalidImageException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.UserAvatarRepository;
import com.behlul.liftlog.repository.UserRepository;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ProfileService {

    /** The app uploads a 256×256 JPEG (~30 KB); this leaves room for other clients without allowing abuse. */
    static final int MAX_AVATAR_BYTES = 512 * 1024;

    private final UserRepository userRepository;
    private final UserAvatarRepository avatarRepository;
    private final CurrentUserProvider currentUser;
    private final WeightService weightService;
    private final Clock clock;

    public ProfileService(UserRepository userRepository, UserAvatarRepository avatarRepository,
                          CurrentUserProvider currentUser, WeightService weightService, Clock clock) {
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.currentUser = currentUser;
        this.weightService = weightService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        return toResponse(loadUser());
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = loadUser();
        String name = request.displayName() == null || request.displayName().isBlank()
                ? null : request.displayName().trim();
        user.setDisplayName(name);
        user.setHeightCm(request.heightCm());
        BigDecimal newWeight = request.bodyWeightKg();
        if (newWeight != null && !sameWeight(newWeight, user.getBodyWeightKg())
                && newWeight.compareTo(WeightService.MAX_LOGGED_KG) <= 0) {
            // Only a changed weight is logged, so saving the name does not overwrite today's weigh-in
            weightService.recordFromProfile(user.getId(), newWeight);
        }
        user.setBodyWeightKg(newWeight);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse setShareTraining(boolean enabled) {
        User user = loadUser();
        user.setShareTraining(enabled);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse replaceAvatar(byte[] data) {
        if (data.length == 0) {
            throw new InvalidImageException("Image is empty");
        }
        if (data.length > MAX_AVATAR_BYTES) {
            throw new InvalidImageException("Image must be at most " + (MAX_AVATAR_BYTES / 1024) + " KB");
        }
        ImageFormat format = ImageFormat.detect(data)
                .orElseThrow(() -> new InvalidImageException("Only JPEG, PNG or WebP images are accepted"));

        Long userId = currentUser.currentUserId();
        UserAvatar avatar = avatarRepository.findById(userId).orElseGet(() -> new UserAvatar(userId));
        avatar.replace(format.mediaType(), data, LocalDateTime.now(clock));
        avatarRepository.save(avatar);
        return toResponse(loadUser(), avatar.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public UserAvatar getAvatar() {
        Long userId = currentUser.currentUserId();
        return avatarRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Avatar of user", userId));
    }

    @Transactional
    public void deleteAvatar() {
        avatarRepository.deleteById(currentUser.currentUserId());
    }

    private static boolean sameWeight(BigDecimal a, BigDecimal b) {
        return b != null && a.compareTo(b) == 0;
    }

    private User loadUser() {
        Long id = currentUser.currentUserId();
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private ProfileResponse toResponse(User user) {
        return toResponse(user, avatarRepository.findUpdatedAt(user.getId()).orElse(null));
    }

    private static ProfileResponse toResponse(User user, LocalDateTime avatarUpdatedAt) {
        Long avatarVersion = avatarUpdatedAt == null ? null : avatarUpdatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
        return new ProfileResponse(user.getId(), user.getDisplayName(), user.getHeightCm(), user.getBodyWeightKg(),
                avatarVersion, user.isShareTraining());
    }
}
