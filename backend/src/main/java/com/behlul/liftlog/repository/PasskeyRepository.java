package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.Passkey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {

    List<Passkey> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<Passkey> findByCredentialId(String credentialId);

    /** Part of a password change, which runs in its own transaction. */
    @Modifying
    @Query("delete from Passkey p where p.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    /** Called right after a verified sign-in, outside any other transaction, so it brings its own. */
    @Transactional
    @Modifying
    @Query("update Passkey p set p.signatureCount = :count, p.lastUsedAt = :now where p.id = :id")
    int recordUse(@Param("id") Long id, @Param("count") long count, @Param("now") LocalDateTime now);
}
