package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

    /** Only the timestamp, so building a profile response never reads the image bytes. */
    @Query("select a.updatedAt from UserAvatar a where a.userId = :userId")
    Optional<LocalDateTime> findUpdatedAt(@Param("userId") Long userId);
}
