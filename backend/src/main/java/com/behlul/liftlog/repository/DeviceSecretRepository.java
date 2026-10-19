package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.DeviceSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceSecretRepository extends JpaRepository<DeviceSecret, Long> {

    /**
     * Most recently used first; a secret that was never marked used counts as the oldest. Eviction drops
     * the tail of this list, and login tries the head first.
     */
    @Query("select d from DeviceSecret d where d.userId = :userId order by d.lastUsedAt desc nulls last, d.id desc")
    List<DeviceSecret> findByUserIdMostRecentlyUsedFirst(@Param("userId") Long userId);

    @Modifying
    @Query("delete from DeviceSecret d where d.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
