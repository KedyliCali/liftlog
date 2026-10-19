package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.WebAuthnCeremony;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WebAuthnCeremonyRepository extends JpaRepository<WebAuthnCeremony, UUID> {

    /**
     * SELECT ... FOR UPDATE: two requests finishing the same ceremony run one after the other, and the
     * second finds the row already deleted, so a ceremony can never be used twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WebAuthnCeremony c where c.id = :id")
    Optional<WebAuthnCeremony> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("delete from WebAuthnCeremony c where c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
