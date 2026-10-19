package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.DeviceSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceSecretRepository extends JpaRepository<DeviceSecret, Long> {

    /** Newest first: the device that signed in most recently is the most likely one to sign in again. */
    List<DeviceSecret> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying
    @Query("delete from DeviceSecret d where d.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
