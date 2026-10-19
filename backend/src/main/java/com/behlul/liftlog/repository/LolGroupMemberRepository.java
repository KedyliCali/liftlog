package com.behlul.liftlog.repository;

import com.behlul.liftlog.entity.LolGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Memberships are keyed by user id, so findById(userId) is "my membership". */
public interface LolGroupMemberRepository extends JpaRepository<LolGroupMember, Long> {

    /** Join order; the first member is the owner. */
    List<LolGroupMember> findByGroupIdOrderByJoinedAtAscUserIdAsc(Long groupId);

    /** The owner: whoever joined first, so ownership passes on by itself when they leave. */
    Optional<LolGroupMember> findFirstByGroupIdOrderByJoinedAtAscUserIdAsc(Long groupId);

    long countByGroupId(Long groupId);

    /** Asks the database, unlike findById, which may return the copy this transaction loaded earlier. */
    boolean existsByUserIdAndGroupId(Long userId, Long groupId);

    boolean existsByGroupIdAndPuuidAndUserIdNot(Long groupId, String puuid, Long userId);

    /** 0 when the user is not (or no longer) a member of that group. */
    @Modifying
    @Query("delete from LolGroupMember m where m.userId = :userId and m.groupId = :groupId")
    int deleteFromGroup(@Param("userId") Long userId, @Param("groupId") Long groupId);
}
