package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LolGroupRepository extends JpaRepository<LolGroup, Long> {

    /**
     * SELECT ... FOR UPDATE on the group row. Every change to a group's members takes this lock first, so
     * they run one at a time per group: two friends cannot both take the last seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LolGroup> findLockedByInviteCode(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LolGroup> findLockedById(Long id);

    boolean existsByInviteCode(String inviteCode);
}
