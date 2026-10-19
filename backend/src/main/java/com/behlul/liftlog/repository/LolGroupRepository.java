package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LolGroupRepository extends JpaRepository<LolGroup, Long> {

    /**
     * A row lock on the group: Hibernate writes PESSIMISTIC_WRITE as SELECT ... FOR NO KEY UPDATE on PostgreSQL,
     * a lock that conflicts with itself. Every change to a group's members takes it first, so they run one at a
     * time per group: two friends cannot both take the last seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LolGroup> findLockedByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LolGroup> findLockedById(Long id);

    boolean existsByInviteCode(String inviteCode);
}
