# League friend groups: design (built on `feature/lol-groups`)

Built as designed, with two small departures: the join field is a textarea (a single-line input drops the
line breaks of a pasted message and glues the code to the link), and the owner is labelled "Yönetici"
(admin) rather than "Kurucu", because ownership passes on when the founder leaves.

A review with adversarial verification (15 confirmed findings) changed four rules below:
- A view makes at most 4 refresh *attempts*; a Riot error for one player moves on to the next one, and only
  NO_KEY, KEY_REJECTED and RATE_LIMITED stop the loop (the "stop at the first Riot error" rule let one broken
  match stall every board).
- `RiotClient.match` returns Optional: a 404 is skipped, a failing match is skipped for now, and the player is
  still marked refreshed.
- A cached player asked for on another server is moved there (a PUUID survives a transfer); a wrong server
  still gets the 404, because the move rolls back.
- Hibernate sends PESSIMISTIC_WRITE as `FOR NO KEY UPDATE` on PostgreSQL, not `FOR UPDATE`; it conflicts with
  itself, so the serialization argument holds. `LolPlayer` uses `@DynamicUpdate`.

Produced on 2026-10-19 by a design workflow: three independent designs (minimal, friend UX, Riot budget)
and a judge that checked them against the code and synthesized this one. Behlül's decisions on the open
questions: **members only** (no guest Riot IDs) and **one group per user**. Build after the recovery code
(PR #16), which this design relies on for lost devices. Migration number: the next free one after V10.

# RATIONALE
Design 1 is the base. It solves the stated problem with the smallest model (two tables, one singular resource, 8 endpoints), reads the existing code correctly, and every part of it can be explained in an interview: why the code is 10 characters, why it is stored in plain text, why the owner is derived from join order, why Persistable, why FOR UPDATE on the group row. Its weaknesses are mostly in the Riot refresh path and the error contract, and the other designs cover exactly those. From Design 3 comes the real pre-existing bug, verified in HttpRiotClient.call and LolService.refreshQuietly: a 404 from summoner-v4 becomes a 500. Groups make it likely, because a Turkish player picking EUW1 instead of TR1 still gets found by the regional account-v1. From Design 2 come the per-view refresh cap (stalest first), the reserve for actions, the machine-readable 409 reasons for the Turkish UI, and the paste-the-whole-message join. Design 2's in-memory refresh guard, Design 3's background worker and Design 1's SpEL insert-if-absent all try to fix the same problem: two friends opening the board at once refresh the same player twice and can collide on the lol_match_players primary key. All three are replaced by one native query that claims the player's row with SELECT ... FOR NO KEY UPDATE SKIP LOCKED inside the existing transaction. It is enforced by the database, needs no in-memory state or hooks, keeps working with a second instance, makes viewers skip a busy player instead of waiting, and makes the duplicate insert impossible, so lol_match_players keeps its plain save(). Members' own Riot accounts (Designs 1 and 3) are chosen over a shared player list with claims (Design 2), because they remove the 'everyone adds everyone' step entirely. One group per user is kept because the whole user base is under 10 people. Both choices are scope decisions, so they are listed as open questions with these defaults.

## summary
A League group is a set of LiftLog users who joined with the group's 10-character invite code (about 49.5 random bits, shown as K7QXM-2PFD9). Its leaderboard is built at read time from the one Riot account each member links, using the shared lol_players cache, so every member sees the same board and nobody has to follow anybody. Migration V11 adds lol_groups and lol_group_members. lol_group_members has user_id as its primary key, so a user is in at most one group. The owner is the earliest member, so ownership passes on by itself when the owner leaves; when the owner removes a member, the code rotates. The personal follow list stays exactly as it is and becomes the 'Following' half of the League tab. Detail pages are visible to you when you follow the player or when someone in your group linked them. To protect the shared Riot budget: every refresh first claims the player's row with FOR NO KEY UPDATE SKIP LOCKED, so no player is ever refreshed twice at once; a view refreshes at most its 4 stalest players; and background refreshes leave a reserve for following and linking. A pre-existing bug, a Riot ID on the wrong server returning 500 instead of 404, is fixed first.

## dataModel
MIGRATION: backend/src/main/resources/db/migration/V11__lol_groups.sql
- V10 is expected to belong to the recovery-code feature, which ships first on this branch. Before committing, look at the newest file in db/migration. If recovery added no migration, name this file V10.
- Never renumber a version that has already been applied on Neon: Flyway would refuse to start.

-- League friend groups. A group is the LiftLog users who joined with its invite code; its leaderboard is the
-- Riot account each member links. Player and match data stay in the shared lol_players cache (V9).
CREATE TABLE lol_groups (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(32) NOT NULL CHECK (length(btrim(name)) > 0),
    -- 10 symbols from ABCDEFGHJKMNPQRSTUVWXYZ23456789 (no 0/O, 1/I/L), stored without the dash the app shows.
    -- Plain text on purpose: members must be able to share it again, and rotating it revokes an invite
    invite_code VARCHAR(10) NOT NULL UNIQUE CHECK (invite_code ~ '^[A-HJKMNP-Z2-9]{10}$'),
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);

-- user_id is the primary key, so a user is in at most one group. The member who joined first is the owner:
-- there is no owner column that could keep pointing at someone who already left.
CREATE TABLE lol_group_members (
    user_id   BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    group_id  BIGINT       NOT NULL REFERENCES lol_groups(id) ON DELETE CASCADE,
    -- The member's own Riot account; NULL until they link one
    puuid     VARCHAR(100) REFERENCES lol_players(puuid) ON DELETE SET NULL,
    joined_at TIMESTAMP    NOT NULL DEFAULT now(),
    -- An account appears at most once per board. NULLs are distinct, so any number of members can be unlinked.
    -- group_id leads, so this index also serves 'members of group X' and the detail-page visibility check
    CONSTRAINT uq_lol_group_members_account UNIQUE (group_id, puuid)
);

Notes:
- V9 tables do not change.
- No extra index is needed:
  - the user_id primary key serves 'my membership';
  - the (group_id, puuid) unique index serves 'members of a group' and 'is this puuid in my group';
  - the UNIQUE on invite_code serves joins.
- invite_code is VARCHAR, not CHAR(10). That matches a String field under ddl-auto=validate, the same as V9.
- joined_at is set in Java from the injected Clock; the DEFAULT is only a fallback. now() is the transaction start time, so two inserts in one transaction would tie.

ENTITIES
- entity/LolGroup.java
  - @Entity @Table(name = 'lol_groups'); @Id @GeneratedValue(strategy = IDENTITY) Long id; String name; @Column(name = 'invite_code') String inviteCode.
  - created_at is left unmapped; validate ignores unmapped columns.
  - Constructor LolGroup(String name, String inviteCode); rename(String); replaceInviteCode(String); getters.
- entity/LolGroupMember.java implements Persistable<Long>, exactly like LolPlayer.
  - @Id @Column(name = 'user_id') Long userId.
  - @Column(name = 'group_id', nullable = false) Long groupId: a plain id, not @ManyToOne.
  - String puuid.
  - @Column(name = 'joined_at', nullable = false) LocalDateTime joinedAt.
  - @Transient boolean isNew = true, with @PostLoad/@PostPersist markStored().
  - Why Persistable: the id is the user's id, which we assign ourselves. Without it, save() would merge, and a racing join could silently move a user into another group instead of failing on the primary key.
  - Constructor LolGroupMember(Long userId, Long groupId, LocalDateTime joinedAt); linkAccount(String puuid); getters.

REPOSITORIES
- repository/LolGroupRepository extends JpaRepository<LolGroup, Long>:
  - @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<LolGroup> findLockedByInviteCode(String inviteCode). 'Locked' between find and By is ignored by query derivation.
  - @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<LolGroup> findLockedById(Long id).
  - boolean existsByInviteCode(String inviteCode).
- repository/LolGroupMemberRepository extends JpaRepository<LolGroupMember, Long>:
  - List<LolGroupMember> findByGroupIdOrderByJoinedAtAscUserIdAsc(Long groupId). The first element is the owner.
  - Optional<LolGroupMember> findFirstByGroupIdOrderByJoinedAtAscUserIdAsc(Long groupId). The owner.
  - long countByGroupId(Long groupId).
  - boolean existsByUserIdAndGroupId(Long userId, Long groupId). This is a database query; findById could return the entity already cached in the persistence context.
  - boolean existsByGroupIdAndPuuidAndUserIdNot(Long groupId, String puuid, Long userId).
  - @Modifying @Query('delete from LolGroupMember m where m.userId = :userId and m.groupId = :groupId') int deleteFromGroup(@Param('userId') Long userId, @Param('groupId') Long groupId). It returns 0 when the user was not a member of that group.
- repository/LolPlayerRepository:
  - isTrackedBy is replaced; its only caller is loadTracked, and untrack uses the delete count. The replacement is a native query:
      /** Detail pages show players you follow or that someone in your group linked as their own account. */
      select exists (select 1 from lol_tracked_players where user_id = :userId and puuid = :puuid)
          or exists (select 1 from lol_group_members me
                     join lol_group_members other on other.group_id = me.group_id
                     where me.user_id = :userId and other.puuid = :puuid)
    as boolean isVisibleTo(@Param('userId') Long userId, @Param('puuid') String puuid).
  - New native query:
      /**
       * Locks a stale player's row until this transaction ends, so only one request at a time refreshes it.
       * SKIP LOCKED: a row another request is refreshing is skipped, never waited for.
       * NO KEY UPDATE: the key never changes, so following or linking this player (their foreign-key checks take
       * KEY SHARE) is not blocked. Must run inside a transaction, otherwise the lock ends immediately.
       */
      select puuid from lol_players
      where puuid = :puuid and (refreshed_at is null or refreshed_at < :staleBefore)
      for no key update skip locked
    as Optional<String> claimForRefresh(@Param('puuid') String puuid, @Param('staleBefore') LocalDateTime staleBefore).
  - findTrackedBy, countTrackedBy, track and untrack do not change.
- repository/LolMatchPlayerRepository does not change: plain save() stays safe, because only the holder of a player's row lock writes that player's match rows.

RIOT CLIENT
- RiotClient.summoner(platform, puuid) now returns Optional<Summoner>. It is empty when Riot has no League profile for that PUUID on that server.

DTOS (added to dto/LolDtos.java)
  public record GroupNameRequest(@NotBlank @Size(max = 32) String name) {}
  /** Any case, with the display dash or spaces; the service normalizes it. The web app extracts it from a pasted message first. */
  public record JoinGroupRequest(@NotBlank @Size(max = 20) String code) {}
  /** @param player the member's linked Riot account; null until they link one */
  public record LolGroupMemberResponse(Long userId, String displayName, boolean you, boolean owner, LocalDateTime joinedAt, LolPlayerResponse player) {}
  /** @param inviteCode 10 symbols without the dash; members come in join order, so the first one is the owner */
  public record LolGroupResponse(String name, String inviteCode, int maxMembers, List<LolGroupMemberResponse> members) {}
  Linking an account reuses TrackPlayerRequest, so it gets the same Riot ID and platform validation.

EXCEPTIONS
- exception/ForbiddenException (new, maps to 403). A project exception, not Spring Security's AccessDeniedException, so services stay free of Spring Security, as CurrentUserProvider intends.
- exception/LolGroupConflictException extends ConflictException (new).
  - It carries enum Rule {ALREADY_IN_GROUP, GROUP_FULL, ACCOUNT_TAKEN, CANNOT_REMOVE_SELF}.
  - It maps to 409 with fieldErrors {group: RULE}. This mirrors how fieldErrors.riot already carries a machine-readable reason.

## endpoints
All new endpoints live in LolGroupController under /api/v1/lol/group and need a JWT.
- SecurityConfig does not change: anyRequest().authenticated() already applies, so a missing or invalid token gets the existing 401 ApiError.
- Errors use the existing ApiError {timestamp, status, error, message, path, fieldErrors}.
- Only GET, POST, PUT and DELETE are used, because CORS allows no PATCH.
- The resource is singular because a user has at most one group, so group ids never appear in URLs.
- Group-rule conflicts are 409 with fieldErrors.group = ALREADY_IN_GROUP | GROUP_FULL | ACCOUNT_TAKEN | CANNOT_REMOVE_SELF. The message stays English; the web app shows its own Turkish or English text.

1. GET /api/v1/lol/group: the caller's group and board.
   - 200 LolGroupResponse. Up to 4 stale linked players are refreshed first, budget permitting; the rest show cached data.
   - 204 with no body when the caller is in no group. This is a normal state; the web client maps it to null.

2. POST /api/v1/lol/group: create a group.
   - Body {name: 'Duo Queue'}; the name is trimmed.
   - 201 LolGroupResponse. The caller is the only member and the owner; player is null.
   - 400 fieldErrors.name: blank or longer than 32.
   - 409 ALREADY_IN_GROUP: 'You are already in a League group; leave it first'.

3. POST /api/v1/lol/group/join
   - Body {code: 'k7qxm-2pfd9'}: any case; dashes and spaces are ignored.
   - 200 LolGroupResponse. Also 200 when the caller is already in this same group, which makes a repeated tap harmless.
   - 400: blank, or longer than 20.
   - 404 'Invite code <typed> not found'. The same answer for an unknown, rotated or deleted-group code, and for text that cannot be a code after normalization.
   - 409 ALREADY_IN_GROUP: the caller is in a different group.
   - 409 GROUP_FULL: 'This group is full (20 members)'.

4. PUT /api/v1/lol/group: rename. Owner only.
   - Body {name: 'Ranked Gang'}.
   - 200 LolGroupResponse.
   - 400 validation; 403 'Only the group owner can do that'; 404 not in a group.

5. POST /api/v1/lol/group/invite-code: issue a new code. Owner only, no body.
   - 200 LolGroupResponse with the new code. The old code stops working at commit.
   - 403; 404.

6. PUT /api/v1/lol/group/me/account: link or replace the caller's own Riot account.
   - Body TrackPlayerRequest {riotId: 'Pesimist Fidan#2003', platform: 'TR1'}.
   - 200 LolGroupResponse. Linking the account you already have is a no-op 200.
   - 400: the same regexes as following a player.
   - 404 in three cases:
     - not in a group (checked before any Riot request);
     - the Riot ID does not exist;
     - the Riot ID has no League profile on the chosen server.
     The last two give 'Riot ID X was not found on EUW1'.
   - 409 ACCOUNT_TAKEN: another member already linked this account.
   - 503 with fieldErrors.riot = NO_KEY | KEY_REJECTED | RATE_LIMITED | RIOT_ERROR (existing handler).

7. DELETE /api/v1/lol/group/me: leave the group.
   - 204. When the last member leaves, the group and its code are deleted.
   - 404 not in a group.

8. DELETE /api/v1/lol/group/members/{userId}: the owner removes another member. The invite code is replaced in the same transaction.
   - 204.
   - 400 non-numeric id (existing MethodArgumentTypeMismatch handler).
   - 403 not the owner.
   - 404 'Group member <id> not found'. The same answer whether the user exists elsewhere or not at all.
   - 409 CANNOT_REMOVE_SELF: 'Leave the group instead of removing yourself'.

Example 200 body:
{name: 'Duo Queue', inviteCode: 'K7QXM2PFD9', maxMembers: 20, members: [
  {userId: 12, displayName: 'Behlul', you: true, owner: true, joinedAt: '2026-10-19T18:02:11', player: {puuid, gameName: 'Pesimist Fidan', tagLine: '2003', platform: 'TR1', profileIconId, summonerLevel, rank: {tier: 'GOLD', division: 'II', lp: 40, wins: 61, losses: 55}, recentGames: 20, recentWins: 11, refreshedAt: '2026-10-19T18:00:00'}},
  {userId: 15, displayName: null, you: false, owner: false, joinedAt: '2026-10-19T18:30:40', player: null}]}
The leaderboard order stays on the client (sortForLeaderboard), as it is today.

CHANGED EXISTING ENDPOINTS (response shapes unchanged; the Android app does not call /lol at all)
- GET /api/v1/lol/players/{puuid} and POST /api/v1/lol/players/{puuid}/refresh:
  - Now 200 when the caller follows the player OR a member of the caller's group (the caller included) linked them.
  - Otherwise the same 404 as before, now worded 'League player X not found'.
  - The refresh is skipped and cached data returned (200) when the player was refreshed under a minute ago, as today, and also when another request is refreshing that player at this moment.
  - A player Riot no longer has on that server now gives 503 RIOT_ERROR instead of 500.
- GET /api/v1/lol/players: same shape. It now refreshes at most the 4 stalest players per call; before, it refreshed every stale player while the budget allowed.
- POST /api/v1/lol/players: a Riot ID that exists but has no League profile on the chosen server now gives 404 RiotIdNotFound instead of 500.
- DELETE /api/v1/lol/players/{puuid}: unchanged.

GLOBALEXCEPTIONHANDLER ADDITIONS
- ForbiddenException returns 403.
- LolGroupConflictException returns 409 with fieldErrors {group: rule.name()}. Spring picks the most specific handler, so it wins over the ConflictException handler.

## serverLogic
A) RIOT CLIENT FIX. Commit 1, independent of groups.
- RiotClient.summoner returns Optional<Summoner>.
- HttpRiotClient:
  - findAccount and summoner turn a 404 into Optional.empty().
  - leagueEntries, recentMatchIds and match turn a 404 into RiotUnavailableException(RIOT_ERROR) inside call(). Today call() rethrows NotFound raw, so it escapes refreshQuietly as a 500.
- FakeRiotClient returns Optional.of(...). UnconfiguredRiotClient still throws NO_KEY.

B) LOLSERVICE REFRESH RULES. Commit 2.
Constants: STALE_AFTER (10 min), MIN_MANUAL_REFRESH (1 min) and REQUESTS_PER_REFRESH (13) are unchanged. New:
- MAX_REFRESHES_PER_VIEW = 4.
- RESERVED_FOR_ACTIONS = 1 + REQUESTS_PER_REFRESH. That is one account lookup plus one full refresh, which is what a follow or a link needs.

1. private enum RefreshOutcome { SKIPPED, REFRESHED, NO_PROFILE }.

2. private RefreshOutcome refreshIfStale(LolPlayer player, Duration olderThan):
   - If playerRepository.claimForRefresh(puuid, now - olderThan) is empty, return SKIPPED. Either the player is fresh, or another request holds the row and is refreshing it right now; viewers never wait for it.
   - Otherwise the row stays locked until this transaction ends.
   - If riot.summoner(...) is empty, return NO_PROFILE; nothing is changed.
   - Otherwise run today's refresh body: profile, solo rank, 20 match ids, findStoredIds, at most 10 new matches via matchRepository.save, markRefreshed, save. Return REFRESHED.
   - Why this removes the race: only the lock holder writes a player's match rows. The next holder's findStoredIds runs after the previous holder committed. So no (match_id, puuid) row can be inserted twice, and no Riot request is spent twice.

3. static List<LolPlayer> staleFirst(Collection<LolPlayer> players, LocalDateTime staleBefore). A pure function: stale players only, never-refreshed first, then the oldest refreshedAt.

4. public void refreshSomeStale(Collection<LolPlayer> players) walks staleFirst(...):
   - Stop after 4 REFRESHED.
   - Stop when limiter.remaining() < REQUESTS_PER_REFRESH + RESERVED_FOR_ACTIONS (27).
   - NO_PROFILE: log.info and continue.
   - RiotUnavailableException: log.info and stop. When Riot is down or the key is missing, the later players would fail the same way.
   - SKIPPED does not count toward the 4, so a second viewer at the same moment moves on to the next stalest players.

5. public LolPlayer findAndCache(TrackPlayerRequest request), extracted from track(). It runs in the caller's transaction.
   - Parse the platform and Riot ID.
   - riot.findAccount, or throw RiotIdNotFoundException.
   - findById, or create a new LolPlayer; rename; saveAndFlush.
   - refreshIfStale(player, STALE_AFTER). NO_PROFILE throws RiotIdNotFoundException(name#tag, platform), which rolls back a newly created row.
   - Riot errors propagate as 503, as today.

6. track(): countTrackedBy >= MAX_TRACKED (20) gives 409 → findAndCache → playerRepository.track → toResponse. The responses do not change.

7. list(): findTrackedBy → refreshSomeStale → map with toResponse.

8. detail(): loadVisible → refreshSomeStale(List.of(player)) → built as today.

9. refreshNow(): loadVisible, then refreshIfStale(player, MIN_MANUAL_REFRESH).
   - NO_PROFILE throws RiotUnavailableException(RIOT_ERROR, 'Riot has no League profile for this player on ' + platform), which is a 503.
   - Other Riot errors propagate as today.
   - SKIPPED returns the cached data.

10. loadTracked becomes loadVisible: playerRepository.isVisibleTo(me, puuid), else ResourceNotFoundException('League player', puuid).

11. toResponse(LolPlayer) becomes public, for LolGroupService.

Lock ordering: refreshes never wait on a lock (SKIP LOCKED), so two board views cannot deadlock each other. Today they can, when refreshing overlapping players in different orders.

C) service/InviteCode: a package-private final class with pure static functions, like OneRepMax and LolStats.
- ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789' (31 symbols); LENGTH = 10.
- generate(RandomGenerator random) appends ALPHABET.charAt(random.nextInt(31)) ten times. nextInt(bound) rejection-samples, so there is no modulo bias.
- normalize(String typed): toUpperCase(Locale.ROOT) (so Turkish i/I casing cannot interfere), strip whitespace and '-'. Return Optional.of(code) when it is exactly 10 alphabet symbols, otherwise Optional.empty().

D) service/LolGroupService
- Dependencies: LolGroupRepository, LolGroupMemberRepository, UserRepository, LolPlayerRepository, LolService, CurrentUserProvider, Clock, and a private final SecureRandom.
- MAX_MEMBERS = LolService.MAX_TRACKED (20). A board is never bigger than a full follow list.
- NEW_CODE_ATTEMPTS = 5.

Helpers:
- membership(): memberRepository.findById(me), or ResourceNotFoundException('League group membership', me).
- lockGroupOf(member): groupRepository.findLockedById(member.groupId), or 404 (the group was deleted in the meantime). This is a SELECT ... FOR UPDATE.
- requireOwner(group): if findFirstByGroupIdOrderByJoinedAtAscUserIdAsc(group.id).userId differs from me, throw ForbiddenException('Only the group owner can do that').
- newInviteCode(): up to 5 times, InviteCode.generate(random); return the first code where !existsByInviteCode. Otherwise IllegalStateException. That is practically unreachable with 8.2e14 codes, and the UNIQUE constraint is the backstop: a collision becomes a 409 through the existing DataIntegrityViolation handler.
- toResponse(group, refreshStale):
  - members in join order; the owner is the first one;
  - players = playerRepository.findAllById(the linked puuids);
  - if refreshStale, lolService.refreshSomeStale(players);
  - a map from puuid to lolService.toResponse(player) (findAllById does not keep order);
  - display names from userRepository.findAllById(memberIds), copied into a HashMap with a loop, because display_name can be null and Collectors.toMap rejects null values;
  - each member becomes LolGroupMemberResponse(userId, displayName, you, owner, joinedAt, player or null).

Methods. All are @Transactional and read-write, because refreshes write the cache, the same as today's list().
- myGroup() returns Optional<LolGroupResponse>:
  - findById(me) empty → Optional.empty(), and the controller answers 204;
  - otherwise toResponse(group, true).
  - Apart from linking, this is the only group path that spends Riot requests.
- create(request):
  - existsById(me) → ALREADY_IN_GROUP;
  - save new LolGroup(name.trim(), newInviteCode());
  - save new LolGroupMember(me, group.id, now);
  - return toResponse(group, false).
  - A double tap makes the second insert hit the user_id primary key: generic 409, and the transaction rolls back its own group row. The web button is disabled while saving anyway.
- join(request):
  - InviteCode.normalize, else 404;
  - findLockedByInviteCode, else 404;
  - under the lock, look up my membership: same group → return toResponse(group, false); another group → ALREADY_IN_GROUP;
  - countByGroupId >= 20 → GROUP_FULL;
  - save new LolGroupMember(me, group.id, now); return toResponse(group, false).
  - The lock keeps the cap exact when two friends take the last seat at the same moment.
- rename(request): membership → lockGroupOf → requireOwner → group.rename(name.trim()) → toResponse(group, false).
- rotateInviteCode(): membership → lockGroupOf → requireOwner → group.replaceInviteCode(newInviteCode()) → toResponse(group, false).
- linkAccount(request):
  - m = membership(): a 404 before any Riot request;
  - group = lockGroupOf(m). The lock is held across the Riot calls (about 1-3 s), so the owner cannot remove this member halfway and two members of this group cannot claim the same account at once;
  - existsByUserIdAndGroupId(me, group.id) re-checks against the database, because the member may have been removed before the lock was taken; else 404;
  - player = lolService.findAndCache(request): the account lookup and a refresh if stale; 404 and 503 propagate;
  - existsByGroupIdAndPuuidAndUserIdNot(group.id, puuid, me) → ACCOUNT_TAKEN;
  - m.linkAccount(puuid), written by dirty checking;
  - return toResponse(group, false).
- leave():
  - m = membership(); group = lockGroupOf(m);
  - if deleteFromGroup(me, group.id) == 0, return (the owner removed me in the meantime);
  - if countByGroupId == 0, groupRepository.delete(group): no orphan groups, and the code dies with the group.
  - Ownership passes to the next-oldest member without any code.
- removeMember(target):
  - m = membership(); group = lockGroupOf(m); requireOwner(group);
  - target == me → CANNOT_REMOVE_SELF;
  - deleteFromGroup(target, group.id) == 0 → ResourceNotFoundException('Group member', target);
  - group.replaceInviteCode(newInviteCode()), because the removed member still knows the old code.

Transactions and locks:
- Every group mutation locks the group row before it touches member rows, so group operations run one at a time per group and cannot deadlock each other.
- The lock order is always group row, then player rows. Player claims never wait, so there is no lock cycle.
- Only linkAccount calls Riot while holding the group lock. Every other mutation builds its response from the cache.

E) LolGroupController (@RestController @RequestMapping('/api/v1/lol/group')):
- GET: groupService.myGroup().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build())
- POST: @ResponseStatus(CREATED) create(@Valid @RequestBody GroupNameRequest)
- POST /join: join(@Valid @RequestBody JoinGroupRequest)
- PUT: rename(@Valid @RequestBody GroupNameRequest)
- POST /invite-code: rotateInviteCode()
- PUT /me/account: linkAccount(@Valid @RequestBody TrackPlayerRequest)
- DELETE /me: @ResponseStatus(NO_CONTENT) leave()
- DELETE /members/{userId}: @ResponseStatus(NO_CONTENT) removeMember(@PathVariable Long userId)
Swagger picks the controller up automatically.

F) RIOT REQUEST BUDGET. RiotRateLimiter stays as it is (18/s, 90 per 2 min, shared by all users).
- A refresh costs 3 requests plus new matches, at most 13. A link or follow adds 1 account lookup.
- The cache is keyed by puuid and shared. With the row claim, a player costs at most one refresh per 10 minutes, however many members, follow lists or simultaneous viewers include them.
- One view spends at most 4 x 13 = 52 requests, and only while 27 or more remain. Background refreshes therefore always leave 14, enough for one follow or link. Under concurrent views the remaining() check is check-then-act, so the reserve can shrink, but acquire() still never lets Riot's limit be crossed.
- Steady state, 8 linked players at about 4 requests each per 10 minutes: about 7 requests per 2 minutes, against 90.
- Cold morning, all 8 players stale with about 10 new matches each:
  - the first view refreshes 4 (up to 52 requests);
  - the next view refreshes 1 more, then stops below 27;
  - the rest refresh in the next window, and their rows show their refreshedAt until then.
- The web app only fetches the follow list while the Following view is shown, so opening the Group view does not refresh both lists.

G) COMMIT PLAN, small commits in teaching mode:
1. fix: report a Riot ID on the wrong server as not found instead of a 500
2. feat: claim League players with SKIP LOCKED and refresh at most 4 per view
3. feat: League groups schema and entities (V11)
4. feat: League group API with invite codes
5. feat(web): League group view with create, join and manage sheets
6. docs: groups and the Riot budget in RIOT.md, WALKTHROUGH.md and PROGRESS.md

## clientFlow
API LAYER (web/src/api)
- types.ts:
  - LolGroupMember {userId: number; displayName: string | null; you: boolean; owner: boolean; joinedAt: string; player: LolPlayer | null}
  - LolGroup {name: string; inviteCode: string; maxMembers: number; members: LolGroupMember[]}
- endpoints.ts:
  - lolGroup: () => request<LolGroup | undefined>('/api/v1/lol/group').then((g) => g ?? null). request() returns undefined on a 204, and TanStack Query v5 rejects undefined data.
  - createLolGroup(name); joinLolGroup(code); renameLolGroup(name) (PUT); rotateLolInviteCode(); linkLolAccount(riotId, platform) (PUT /me/account).
  - leaveLolGroup() and removeLolGroupMember(userId), both with responseType 'none'.
- queryKeys.ts: lolGroup: ['lol', 'group'].
- client.ts:
  - AppErrorKind gains 'forbidden' and 'groupRule'.
  - 403 → AppError('forbidden').
  - 409 with fieldErrors.group → AppError('groupRule', rule), checked before the plain 409 case.
- lib/format.ts errorMessage: groupRule → t.groupRules[detail] ?? t.errorConflict; forbidden → t.errorForbidden.

PURE HELPERS
- lib/lolGroup.ts (Vitest):
  - normalizeInviteCode(text): toUpperCase(), not toLocaleUpperCase(), so a Turkish iPhone never turns i into a dotted capital I. It strips whitespace and dashes, and returns the code or null.
  - extractInviteCode(text): normalizeInviteCode on the whole text first. Failing that, the last XXXXX-XXXXX group of alphabet symbols in the uppercased text, so pasting the whole WhatsApp message works.
  - formatInviteCode('K7QXM2PFD9') gives 'K7QXM-2PFD9'.
  - inviteMessage(name, code, origin).
  - splitGroupBoard(members) gives {ranked: linked members ordered by sortForLeaderboard on their players, unlinked: members without an account, in join order}.
  - oldestRefresh(players).
- lib/platform.ts: isStandalone, moved from ProfilePage.

SCREENS (left, right, top, bottom are screen directions)
1. League tab (/game, GamePage.tsx):
   - Under the page header, a .segmented switch: GRUP on the left, TAKİP on the right.
   - The choice lives in the URL as ?view=group|following (setSearchParams with replace), so returning from a player page keeps it. With no parameter, the default is group when the user is in a group, otherwise following.
   - A skeleton shows until the group query settles. If the group query fails, the default becomes following, and the Group segment shows ErrorState with Retry.
   - The follow-list query uses enabled: view === 'following'.
2. FOLLOWING: today's page, unchanged (swipe to unfollow, 'Oyuncu ekle' FAB in the lower right).
   - The row markup moves to LolBits.LolBoardRow, shared with the group board.
   - AddPlayerSheet becomes components/RiotIdSheet.tsx {title, submitLabel, onSubmit(riotId, platform)}; PLATFORMS moves with it.
3. GROUP, not in a group: MessageState (ControllerIcon, 'Arkadaşlarınla tek tahta', 'Grup kur ve kodu gönder, ya da arkadaşının koduyla katıl. Gruptaki herkes aynı sıralamayı görür.'). Under it, two stacked buttons: primary 'Grup kur' and tonal 'Koda katıl'. No FAB.
   - CreateGroupSheet:
     - a name field (max 32, trimmed, required) and 'Oluştur', disabled while saving;
     - on success: setQueryData(lolGroup, result), then the 'Riot hesabın' sheet opens right away.
   - JoinGroupSheet:
     - one input (autoCapitalize characters; autoCorrect, spellCheck and autoComplete off; maxLength 400), placeholder 'ABCDE-FGHJK';
     - hint: 'WhatsApp mesajının tamamını yapıştırabilirsin.';
     - a live 'Kod: K7QXM-2PFD9' line when extractInviteCode finds a code. The button stays disabled until then; for typed lookalikes the hint is 'Kodlarda 0, O, 1, I ve L yok';
     - privacy line: 'Gruptakiler profil adını ve bağladığın Riot hesabını görür.';
     - when !isStandalone(): 'Şu an tarayıcıdasın. LiftLog'u ana ekrandan kullanıyorsan oradan katıl; tarayıcı ve ana ekran ayrı hesap tutar.'
     - Inline errors:
       - notFound: 'Bu kodla bir grup yok. Kod yenilenmiş olabilir; arkadaşından yenisini iste.'
       - GROUP_FULL: 'Grup dolu (en fazla 20 kişi).'
       - ALREADY_IN_GROUP: 'Zaten bir gruptasın; önce ondan ayrıl.'
     - On success: setQueryData, then invalidate lolGroup so the GET refreshes the board, then the account sheet opens.
4. GROUP, in a group:
   - Group card at the top:
     - a lime strip down its left edge;
     - the group name in large Barlow Condensed at the top left;
     - '4/20' muted at the top right, next to a gear icon button that opens the Manage sheet;
     - the invite chip 'K7QXM-2PFD9', with a share icon inside, straddling the card's bottom edge on the right, so the card does not read as one flat box. The code is set in the body font with letter spacing, because condensed glyphs blur S/5 and B/8.
   - Under the card, a muted line from oldestRefresh: t.updatedAgo, e.g. '12 dk önce güncellendi'.
   - Share:
     - navigator.share({text}), with the link inside the text only; AbortError is ignored;
     - otherwise navigator.clipboard.writeText plus the toast 'Davet kopyalandı';
     - TR text: 'LiftLog'da League grubuma katıl: {name} / Davet kodu: {code} / {origin} / Uygulamada: League → Grup → Koda katıl (iPhone'da önce Paylaş → Ana Ekrana Ekle)', one item per line.
     - No deep link: on iPhone a tapped link opens Safari, whose storage and device account are separate from the home-screen app.
   - If my membership has no player: a banner under the card, 'Tahtada görünmek için Riot hesabını bağla', with 'Hesabımı bağla' on its right. It opens RiotIdSheet ('Riot hesabın', 'Bağla'):
     - success: setQueryData(lolGroup, result);
     - notFound: riotIdNotFound(id, platform), the existing string, which also covers a wrong server;
     - ACCOUNT_TAKEN: 'Bu hesabı başka bir üye bağlamış. Eski telefonunsa grubu kuran onu çıkarabilir.';
     - 503: the existing riot messages.
   - If my displayName is null: a muted line 'Arkadaşların seni Üye olarak görüyor; adını Profil'den ekle', linking to /profile.
   - Board: ranked linked members drawn with LolBoardRow.
     - The member's display name sits in muted text under the Riot name.
     - My row gets class 'you': a lime bar on its left edge and a small 'SEN' tag overlapping its top-right corner.
     - Tapping a row opens /game/:puuid, which the server now allows through the group.
     - No swipe-to-delete, no FAB.
   - Under the board: 'Henüz hesap bağlamadı: Ali, Üye'.
5. Manage sheet (bottom sheet from the gear):
   - For everyone:
     - 'Hesabımı değiştir' opens RiotIdSheet again;
     - a member list: initials, name, 'KURUCU' and 'SEN' chips, the linked Riot name or 'hesap bağlamadı', and 'katıldı: 19 Eki';
     - a red 'Gruptan ayrıl' at the bottom, with window.confirm text that depends on the case:
       - member: 'Ayrılırsan bu tahtayı göremezsin.'
       - owner with others: 'Yönetim şu üyeye geçer: {name}.' (worded so no Turkish suffix attaches to the name)
       - last member: 'Grupta başka kimse yok; ayrılınca grup silinir.'
     - Leave → DELETE /me → setQueryData(lolGroup, null) → the not-in-group state, plus a toast.
   - Owner only:
     - a name field with 'Kaydet' (PUT → setQueryData);
     - 'Yeni davet kodu', confirm 'Eski kod hemen çalışmaz olur. Gruptakiler etkilenmez.' → setQueryData and a toast;
     - 'Çıkar' on the right of every other member, confirm → DELETE → invalidate → toast 'Çıkarıldı; davet kodu değişti'.
6. The player detail page is unchanged. After a manual refresh it invalidates lolGroup as well as lolPlayers.

CSS (app.css): .lol-group-card (panel with a ::before lime edge), .invite-chip (negative bottom margin so it overlaps the card edge), .lol-row.you with the SEN corner tag, .lol-member-name, .lol-unlinked, .group-banner.

i18n (tr and en): lolViewGroup, lolViewFollowing, groupEmptyTitle, groupEmptyBody, createGroup, joinGroup, groupName, joinCodeHint, joinCodePreview(code), inviteCodeLookalikes, groupPrivacyHint, joinInBrowserHint, inviteCodeNotFound, membersCount(n, max), shareCode, codeCopied, inviteText(name, code, url), manageGroup, newInviteCode, newInviteCodeConfirm, inviteCodeChanged, leaveGroup, leaveConfirmMember, leaveConfirmOwner(name), leaveConfirmLast, removeMember, removeMemberConfirm(name), memberRemoved, linkAccount, linkAccountTitle, linkAccountBanner, changeAccount, notLinkedYet(names), memberFallback, you, owner, joinedOn(date), setNameHint, errorForbidden, groupRules {ALREADY_IN_GROUP, GROUP_FULL, ACCOUNT_TAKEN, CANNOT_REMOVE_SELF}.

SESSION AND STATE
- Membership is stored on the server per user; the JWT carries only the user id.
- When the 30-day token expires, the device logs in again with its stored credentials and is still in the group.
- Leaving or being removed takes effect on the next request:
  - GET returns 204 and the view switches to not-in-group;
  - detail pages for players visible only through the group return 404 (ErrorState).
- A 403 on an owner action (ownership changed after the screen was drawn) shows the errorForbidden toast and invalidates lolGroup.
- When the owner leaves, the next GET shows the next-oldest member as owner, and the owner controls appear for them.

OLD AND SECOND DEVICES
- An old web build cached by the service worker never calls /lol/group, and /lol/players keeps its shape, so it keeps working. The switch appears after the workbox autoUpdate.
- If the web app goes live before the API: GET /lol/group returns 404 'No endpoint', Following is the default view, and the Group segment shows ErrorState for a few minutes.
- The Android app has no League screens; the migration and endpoints are additive.
- A second device, Safari next to the home-screen app, or a phone whose site data was cleared has a different device account and is not in the group.
  - Joining there creates a second membership. Linking the same Riot account then gets ACCOUNT_TAKEN with the 'ask the owner' hint. The owner removes the stale member; the join date tells them apart.
  - The real fix is the recovery code (the first feature on this branch). Membership is per user, so restoring the original account brings the group back on that device.

## security
THREATS AND HOW EACH IS HANDLED

1. Entropy and guessing
- A code is 10 symbols from a 31-symbol alphabet: 31^10 ≈ 8.2e14, about 49.5 bits. Symbols come from SecureRandom.nextInt(31), which rejection-samples, so there is no modulo bias.
- Joining needs a JWT, but device accounts are free, so attackers count as authenticated.
- Worst case: 300 guesses/s for a year (about 9.5e9, more than a free Render instance serves) against 10 live codes. That gives about 1e-4 expected hits.
- 8 characters (40 bits) would give about 9% and would need a failed-attempt limiter. A limiter's global window lets one person with free accounts block every friend's join, and it adds in-memory state. Length is chosen over a limiter. People paste the code, so the extra two characters cost little.
- The alphabet has no look-alikes (no 0/O, 1/I/L).

2. Brute-force amplification: none.
- Unknown and malformed codes get the same 404 after one indexed lookup and never touch Riot.

3. Hashing: the code is stored in plain text on purpose.
- It is a shareable capability that every member must be able to see again in order to share it.
- It only unlocks group-level data that sits in the same database.
- Rotation makes it disposable.
- A hash of a roughly 50-bit code would also fall to offline brute force.
- Contrast for the interview: passwords and the recovery code stay BCrypt-hashed, because they unlock a whole account.
- The code is never logged and never put in a URL; it travels in a POST body and in the shared text. It never reaches the public repository, because codes are generated at runtime.

4. Enumeration
- Group ids never appear in URLs or responses (singular resource).
- Removing a user who is outside your group gives the same 404 as a user who does not exist; one conditional DELETE does the check.
- Detail pages still answer 404 for players that are not visible, so other users' follows and groups cannot be probed.
- 409 GROUP_FULL and a 200 only reach someone who already holds a valid code.

5. Timing
- PostgreSQL compares the code through an equality lookup on a unique B-tree index.
- Malformed and unknown codes get an identical response, so timing reveals nothing beyond the 404 itself.

6. Rotation
- The owner can rotate at any time.
- Removing a member rotates automatically, so the removed person cannot simply rejoin.
- The old code is dead the moment the transaction commits. When the last member leaves, the group and its code are deleted.
- No expiry: friends join days later from the same message, and rotation covers leaks.

7. Revocation versus 30-day JWTs
- Membership and ownership are read from the database on every request; the token carries only the user id.
- Leaving or removal takes effect on the next request, and so does the end of visibility for group-only players.

8. Authorization
- The owner is the earliest member (joined_at, then user_id), computed per request.
- Rename, rotate and remove throw ForbiddenException (403).
- Members can only change their own row (link, leave).
- The acting user always comes from CurrentUserProvider. The only id in a path is the target member, and it is checked to belong to the caller's group under the group lock, which prevents IDOR.

9. Impersonating a Riot account
- A personal key cannot use Riot Sign-On, so ownership of an account cannot be proven. This is trust-based within a friend group.
- Mitigations:
  - UNIQUE(group_id, puuid): one claim per account per board;
  - every member's display name is shown next to their account;
  - the owner can remove an impostor, which also rotates the code.
- Possible later: a profile-icon-change challenge.

10. Riot budget abuse (the key is shared by everyone)
- Membership is checked before any Riot request.
- Linking costs exactly what following costs.
- Caps: 20 members, one group per user, 4 refreshes per view.
- Background refreshes keep a 14-request reserve, and the SKIP LOCKED claim means a player is never refreshed twice at once.
- RiotRateLimiter still hard-caps every outgoing request, so the key never earns a Riot 429 penalty.
- A hostile member linking new accounts in a loop can use up a 2-minute window. That is the same exposure the follow endpoint has today; boards then show cached data.

11. Data exposure
- Members see each other's display_name, join date, sequential user id and linked Riot ID (public game data).
- No workouts, nutrition, body data, avatar or email are exposed. The join sheet says this up front.

12. Input handling
- Name: @NotBlank, @Size(32), trim, and a database CHECK.
- Code: @Size(20), strict normalization, and a database CHECK on the stored format.
- Riot ID: the existing TrackPlayerRequest regexes.
- React escapes all names. Every query is parameterized.

13. Integrity under concurrency
- FOR UPDATE on the group row serializes join, leave, remove, rename, rotate and link within a group, which keeps the member cap and 'delete when empty' exact.
- Primary-key and unique constraints are the last line of defense, mapped to 409.

CORS, CSRF (stateless bearer tokens) and secrets do not change, and the feature needs no new environment variables.

## edgeCases
- Joining with the code of the group you are already in (double tap or re-paste) returns 200 and the member count does not change.
- Creating or joining while already in another group returns 409 ALREADY_IN_GROUP ('leave first'). A double-tapped create hits the user_id primary key, gets a generic 409 and rolls back its own group row. The button is disabled while saving.
- The 21st join returns 409 GROUP_FULL. Two joins racing for the 20th seat are serialized by the FOR UPDATE lock on the group row, so exactly one wins.
- When the owner leaves and others remain, the next-oldest member becomes owner on the next GET. When the last member leaves, the group and its code are deleted, and the old code then returns 404.
- When the owner removes a member: 204, and the code changes in the same transaction. The removed member's next GET returns 204, their group-only detail pages return 404, and the old code no longer works.
- The owner removing themselves gets 409 CANNOT_REMOVE_SELF. Removing an id that is not in the owner's group (another group's member or a user that does not exist) gets the same 404.
- A non-owner calling rename, rotate or remove gets 403. The UI hides those controls, so this only happens when ownership changed between render and tap; the client shows a toast and refetches.
- Two members linking the same Riot account: the second gets 409 ACCOUNT_TAKEN. Simultaneous links are serialized by the group lock, and UNIQUE(group_id, puuid) is the backstop.
- Re-linking a different account replaces the row. The old player stays in the shared cache and in anyone's follow list. Re-linking the same account is a no-op 200.
- A Riot ID that exists but was picked with the wrong server (TR1 versus EUW1) now gets 404 'X was not found on EUW1', which tells the user to check the server. Before this fix it was a 500.
- A linked player who later moves servers: the quiet refresh gets NO_PROFILE, logs it and keeps the cached row, so the board still loads (today it would be a 500 for every member). A manual refresh gives 503 RIOT_ERROR, and the member re-links on the new server.
- Several friends open the board within seconds of a WhatsApp ping. The first request claims the stalest players; the others skip those rows (SKIP LOCKED) and either refresh the next stalest or show the cache. No player is refreshed twice and no match row is inserted twice, so a GET can no longer fail with 409.
- A 20-player board where everyone is stale after a night: each view refreshes the 4 stalest at most, rows show their refreshedAt, and the muted line under the card shows the oldest data's age.
- Riot key missing, rejected, or budget used up: the board loads from the cache, the refresh loop stops at the first Riot error, and linking returns 503 with the existing messages.
- A Riot ID that does not exist returns 404 RiotIdNotFound, the same as when following one.
- A code typed in lowercase, with the dash or spaces, or inside the whole pasted WhatsApp message is extracted by the client (the last XXXXX-XXXXX group) and normalized again by the server. Look-alike characters never occur in codes, so typing them shows a local hint, and the server would answer 404.
- Turkish keyboard: the server uppercases with Locale.ROOT and the client uses toUpperCase() rather than toLocaleUpperCase(), so i never becomes a dotted capital I.
- iOS keeps separate storage for Safari and the home-screen app. So the share text has no deep link, the join sheet in a browser tab warns about the second account, and the recovery code can move an account between the two.
- A lost or reset phone leaves a ghost membership that still holds the Riot account. The owner removes it; the join date tells it apart. If the ghost is the owner, only account recovery helps; otherwise the others leave and create a new group.
- A player who is both in my follow list and on the group board appears in both views. Unfollowing does not remove them from the group view or from their detail page.
- Members who have not linked an account are not ranked. They are listed under the board by displayName, or 'Üye' when it is null. If my own displayName is null, a hint points me to Profile.
- Coming back from a player page returns to the same view, because ?view= is in the URL.
- Being removed while the link sheet is open: linking returns 404 (membership is re-checked under the group lock before any Riot call), and the refetch switches the view to not-in-group.
- Group names that are blank or only spaces get 400, names longer than 32 characters get 400, and the database CHECK backs this up. An empty board is impossible, because the creator is always a member.
- Web deployed before the API: the Group segment shows ErrorState ('No endpoint' 404) and Following is the default view until the API deploy finishes.
- The Android app and old cached web builds are unaffected: no existing endpoint changes shape.
- Render cold start: the first view after an idle period waits for the wake-up (the existing WakeUpHint) plus at most 4 refreshes.
- Riot renames: names are only updated on account lookups (follow or link), as today.
- There is no account deletion today. If one is added, deleting the last member must also delete the now-empty group. Deleting an owner simply passes ownership on, because the owner is derived.

## tests
- ApiIntegrationTest setup note: use a unique Riot name per test, for example 'Own' plus 6 random letters with tag TR1. The fake client derives the PUUID from the name, and lol_players rows are shared across tests in the same database.
- ApiIntegrationTest.lolGroupSharesOneBoardThroughAnInviteCode (fake Riot). Register owner, friend and stranger.
- Owner GET /lol/group returns 204.
- Owner POST /lol/group {name: ' Duo Queue '} returns 201 with $.name 'Duo Queue', $.inviteCode matching ^[A-HJKMNP-Z2-9]{10}$, 1 member with owner and you true, and player null.
- Owner PUT /me/account returns 200 with members[0].player.gameName set and recentGames 10.
- Friend POST /join with the code lowercased and a dash after 5 characters returns 200 with 2 members, members[1].you true and owner false.
- The same join again returns 200 and the count stays 2.
- Friend links their own account. Both users' GET returns the same two puuids.
- Friend GET /lol/players/{ownerPuuid} returns 200 although they never followed that player, and GET /lol/players returns length 0.
- Stranger: the same detail returns 404, and GET /lol/group returns 204.
- ApiIntegrationTest.lolGroupOwnerRulesRotationAndLeaving. The owner creates a group and links an account; a member joins.
- Member POST /lol/group returns 409 with fieldErrors.group ALREADY_IN_GROUP.
- Stranger joining 'AAAAA-AAAAA' returns 404, and joining 'O0I1L-XXXXX' returns 404.
- Member linking the owner's Riot ID returns 409 with fieldErrors.group ACCOUNT_TAKEN.
- Member PUT /lol/group and POST /invite-code return 403.
- Owner PUT {name: 'Ranked'} returns 200.
- Owner POST /invite-code returns a different code; the stranger joining with the old code gets 404.
- Owner removing their own userId returns 409 CANNOT_REMOVE_SELF.
- Owner removing the stranger's id (from the register response) returns 404.
- Owner DELETE /members/{memberId} returns 204. The member's GET then returns 204, the member's detail of the owner's puuid returns 404, and the owner's GET shows a changed code.
- The member rejoins with the new code. The owner leaves (204), and the member's GET shows one member with owner true.
- The member leaves. The stranger joining with the last code gets 404, because the group was deleted.
- ApiIntegrationTest.refreshClaimLetsOneRequestRefreshAPlayerAtATime. Autowire LolPlayerRepository and PlatformTransactionManager, use a TransactionTemplate, and save a never-refreshed LolPlayer.
- Thread A, in a transaction: claimForRefresh(puuid, now) is present. A then signals a latch and waits.
- Main thread, in another transaction: claimForRefresh(puuid, now) is empty (skipped, without waiting).
- Release A, which commits.
- After markRefreshed(now) is saved: claimForRefresh(puuid, now minus 10 min) is empty (fresh), and claimForRefresh(puuid, now plus 1 min) is present.
- The existing ApiIntegrationTest.lolTrackerFollowsPlayersAndKeepsThemPrivate passes unchanged. It guards the track() refactor, the stranger 404 and repeated follows.
- HttpRiotClientTest (MockRestServiceServer, as already used there):
- summonerNotFoundIsEmpty: a 404 on summoner-v4 gives Optional.empty().
- missingMatchIsRiotError: a 404 on match-v5 throws RiotUnavailableException with reason RIOT_ERROR.
- The existing unknownRiotIdIsEmptyNotAnError still passes.
- service/LolServiceTest (new; Mockito repositories, FakeRiotClient wrapped in a Mockito spy, ManualClock-style fixed Clock):
- refreshesAtMostFourStalestFirst: 6 stale players, one never refreshed, all claimable. summoner is called 4 times, never-refreshed first, then oldest.
- skipsPlayersAnotherRequestHoldsAndKeepsGoing: the stalest claim returns empty, and the next 4 are refreshed.
- keepsAReserveForActions: with a mocked limiter, remaining() 26 means no claim and no Riot call; 27 means one refresh.
- stopsAtTheFirstRiotError: summoner throws RATE_LIMITED; no further player is attempted, and nothing escapes.
- wrongServerIsRiotIdNotFound: findAccount is present and summoner is empty, so track throws RiotIdNotFoundException.
- manualRefreshWithoutProfileIsRiotError: refreshNow throws RiotUnavailableException with RIOT_ERROR.
- staleFirstOrdersNeverRefreshedThenOldest: tests the pure helper.
- service/InviteCodeTest:
- 1,000 codes from new Random(42) are all 10 long and match the alphabet regex, and the same seed gives the same codes.
- 'k7qxm-2pfd9' and ' K7QXM 2PFD9 ' both normalize to 'K7QXM2PFD9'.
- 'K7QXM-2PFD0', 'K7QXO2PFD9', 'ABC' and an 11-symbol code normalize to empty.
- service/LolGroupServiceTest (Mockito, like WorkoutServiceTest):
- joinRejectsAFullGroup: count 20 → GROUP_FULL, and nothing is saved.
- joiningYourOwnGroupAgainReturnsIt: no save.
- joiningWhileInAnotherGroupConflicts.
- lastMemberLeavingDeletesTheGroup.
- leavingWithOthersLeftKeepsTheGroup.
- onlyTheEarliestMemberIsOwner: rename by the second member throws ForbiddenException.
- ownerCannotRemoveThemselves.
- removingAMemberReplacesTheInviteCode.
- linkingForANonMemberNeverCallsRiot: lolService.findAndCache is never invoked.
- linkingAnAccountTakenByAnotherMemberConflicts.
- web/src/lib/lolGroup.test.ts (Vitest):
- normalizeInviteCode accepts lowercase, dashes and spaces, and rejects look-alikes and wrong lengths.
- extractInviteCode finds the code in the Turkish share message (the last group, not the URL), in a bare code and in 'kod: k7qxm 2pfd9', and returns null for unrelated text.
- formatInviteCode gives 'K7QXM-2PFD9'.
- inviteMessage contains the formatted code and the origin.
- splitGroupBoard ranks linked members with sortForLeaderboard, keeps the you and owner flags on the right entries, and lists unlinked members in join order.
- oldestRefresh ignores null refreshedAt.
- web/src/api/client.test.ts (new; fetch stubbed with vi.stubGlobal as in session.test.ts):
- 409 with fieldErrors.group GROUP_FULL gives kind 'groupRule' with detail 'GROUP_FULL'.
- A plain 409 is still 'conflict' with the message.
- 403 gives 'forbidden'.
- 503 with riot NO_KEY is still 'riotNoKey'.
- web/src/api/endpoints.test.ts (new): api.lolGroup() resolves to null on a 204 and to the parsed group on a 200.
- web/src/lib/format.test.ts: errorMessage returns the groupRules strings for every rule, falls back to errorConflict for an unknown rule, and returns errorForbidden for 'forbidden'.
- Manual check before saying it is done:
- Run the local Docker API with RIOT_FAKE=true and the web app in two browser profiles at phone width.
- Walk through create, share/copy, paste the whole message to join, link, check that both boards are identical, remove (and see the code change), and leave (and see the owner hand-over).
- Check that ?view survives a trip to a player page, and check light and dark themes.
- Then run ./mvnw verify, npm test, npm run lint and npm run build.

## files
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/resources/db/migration/V11__lol_groups.sql (V10 if the recovery-code work adds no migration)
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/entity/LolGroup.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/entity/LolGroupMember.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/repository/LolGroupRepository.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/repository/LolGroupMemberRepository.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/service/InviteCode.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/service/LolGroupService.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/controller/LolGroupController.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/exception/ForbiddenException.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/exception/LolGroupConflictException.java (extends ConflictException, Rule enum)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/exception/GlobalExceptionHandler.java (403 for ForbiddenException; 409 with fieldErrors.group)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/service/LolService.java (findAndCache, refreshIfStale with claim, staleFirst, refreshSomeStale, reserve, loadVisible, public toResponse)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/repository/LolPlayerRepository.java (isTrackedBy replaced by isVisibleTo; claimForRefresh)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/dto/LolDtos.java (GroupNameRequest, JoinGroupRequest, LolGroupMemberResponse, LolGroupResponse)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/riot/RiotClient.java (summoner returns Optional)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/riot/HttpRiotClient.java (404 handling)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/main/java/com/behlul/liftlog/riot/FakeRiotClient.java and riot/RiotConfig.java (UnconfiguredRiotClient) for the Optional signature
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/test/java/com/behlul/liftlog/ApiIntegrationTest.java (two group tests, claim test)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/backend/src/test/java/com/behlul/liftlog/riot/HttpRiotClientTest.java (404 cases)
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/test/java/com/behlul/liftlog/service/LolServiceTest.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/test/java/com/behlul/liftlog/service/InviteCodeTest.java
- ADD C:/Users/virus-pc/Desktop/liftlog/backend/src/test/java/com/behlul/liftlog/service/LolGroupServiceTest.java
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/api/types.ts (LolGroup, LolGroupMember)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/api/endpoints.ts (8 group calls; 204 mapped to null)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/api/queryKeys.ts (lolGroup)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/api/client.ts (forbidden and groupRule kinds)
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/api/client.test.ts
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/api/endpoints.test.ts
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/lib/lolGroup.ts
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/lib/lolGroup.test.ts
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/lib/platform.ts (isStandalone moved from ProfilePage)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/lib/format.ts and format.test.ts (errorMessage for the new kinds)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/lib/i18n.ts (group strings, tr and en)
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/components/RiotIdSheet.tsx (AddPlayerSheet generalized; PLATFORMS moves here)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/components/LolBits.tsx (LolBoardRow shared by both views)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/components/Icons.tsx (ShareIcon, GearIcon)
- ADD C:/Users/virus-pc/Desktop/liftlog/web/src/pages/LolGroupView.tsx (group card, board, create, join and manage sheets)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/pages/GamePage.tsx (Group/Following switch with ?view, follow-list query only while Following is shown)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/pages/LolPlayerPage.tsx (also invalidate lolGroup after a refresh)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/pages/ProfilePage.tsx (import isStandalone from lib/platform)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/web/src/app.css (.lol-group-card, .invite-chip, .lol-row.you, .lol-member-name, .lol-unlinked, .group-banner)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/docs/RIOT.md (groups, row claim, 4 per view, reserve)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/docs/WALKTHROUGH.md (interview notes: code entropy versus a limiter, plain-text code versus hashed recovery code, FOR UPDATE on the group row, derived owner, Persistable, SKIP LOCKED and NO KEY UPDATE)
- CHANGE C:/Users/virus-pc/Desktop/liftlog/PROGRESS.md (session entry)

## risks
- Riot account ownership cannot be proven: a personal key cannot use Riot Sign-On, so linking is trust-based. An impostor can claim a friend's account first, and the owner then has to notice and remove them. Acceptable for fewer than 10 friends; a profile-icon challenge can be added later.
- Ghost owner: the owner is the oldest member, so if the owner's device account is lost before the recovery-code feature exists, nobody can rename, rotate or remove. Ship groups after recovery codes, as the user ordered.
- One group per user is enforced by the primary key. Relaxing it later means a composite key and group ids in every URL, which is a breaking change for the web app (open question 2).
- Refreshes still run inside the request's transaction. A view holds one of 5 Hikari connections, plus the claimed player rows, while it waits on Riot. The cap of 4 bounds this. The Riot RestClient has no read timeout configured today, and adding one of a few seconds is a cheap follow-up.
- limiter.remaining() is check-then-act across threads, so under concurrent views the 14-request reserve can shrink and a link can occasionally get 503 RATE_LIMITED. acquire() still guarantees that Riot's own limit is never crossed.
- SKIP LOCKED and NO KEY UPDATE are new concepts for the developer and must be explainable in an interview. The two-thread claim test is the proof and must stay green. The claim only works inside a transaction, which all callers have.
- Changing RiotClient.summoner to Optional touches the fake, the unconfigured client and the HTTP client in one commit. HttpRiotClientTest and the new LolServiceTest must cover it, because the fake client never returns empty.
- Migration numbering: if the recovery-code and group migrations pick numbers independently, Flyway fails at startup on Neon. Decide the number when committing, and never renumber an applied version.
- iOS storage split: Safari and the home-screen app are different device accounts. The share text, the join-sheet warning and the recovery code mitigate it, but a friend can still join from the wrong one. Verify the behavior on Behlül's iPhone.
- Two users following or linking the same brand-new player at the same instant can still collide on the lol_players primary key (generic 409). This race already exists today and is rare.
- Deploy order: the Render static site can go live a few minutes before the Docker API, and the Group segment shows ErrorState until the API catches up.
- Scope and process: this is a sizable feature in teaching mode, and the branch currently bundles three features although CLAUDE.md asks for one feature per branch. Consider cutting feature/lol-groups after recovery merges, and build it in the six small commits listed.

## graftedIdeas
- From Design 3: fix the verified pre-existing bug. HttpRiotClient.call rethrows HttpClientErrorException.NotFound, and refreshQuietly only catches RiotUnavailableException, so a summoner-v4 404 becomes a 500. A Riot ID on the wrong server is now a 404 RiotIdNotFound when following or linking, a logged skip on quiet refreshes, and a 503 RIOT_ERROR on manual refresh.
- From Design 3 and Design 2: one refresh per player at a time (single-flight). Implemented here as a database row claim, SELECT ... FOR NO KEY UPDATE SKIP LOCKED, instead of in-memory claims or a guard released in afterCompletion. It needs no state or hooks, survives multiple instances, never makes a viewer wait, and makes Design 1's insert-if-absent unnecessary.
- From Design 2: refresh at most 4 players per view, stalest first, through a pure, testable staleFirst helper. Applied to the personal follow list too.
- From Design 2 and Design 3: background refreshes keep a reserve for interactive actions. Here it is one lookup plus one full refresh (14 requests).
- From Design 2 and Design 3: machine-readable 409 reasons in fieldErrors.group, following the existing fieldErrors.riot convention, so the Turkish UI never shows English server text or guesses which 409 it got.
- From Design 2 and Design 3: a project ForbiddenException for 403 instead of Spring Security's AccessDeniedException in a service.
- From Design 2: extractInviteCode, so a friend can paste the whole WhatsApp message. Also the share-sheet details: the link goes inside the text only, AbortError is ignored, and the clipboard is the fallback.
- From Design 2: leave confirmations that depend on the case (member, owner with successor, last member), worded so no Turkish suffix attaches to a name.
- From Design 2: the join date in the member list, so a ghost from an old device can be told apart.
- From Design 3: re-check membership under the group lock and before any Riot request when linking. The lock is held across the link so a removal cannot interleave.
- From Design 3: a muted line showing the age of the oldest data on the board, and isStandalone moved to lib/platform.ts so the join sheet can warn when it is opened in a browser tab instead of the home-screen app.

## rejectedIdeas
- Design 2's shared player list with guest rows and claims (claimed_by, composite FK cascade, claim/unclaim, removable matrix): more concepts and endpoints. Members' own accounts already remove the 'everyone adds everyone' step. Guests are deferred to open question 1; they can be added later as an additive table.
- Multiple groups per user with group ids in URLs, a board selector and a stored selection (Designs 2 and 3): unnecessary for under 10 users, and the singular resource removes the IDOR and enumeration surface. See open question 2.
- 8-character 40-bit codes plus an in-memory failed-attempt limiter with a global window (Designs 2 and 3): the global window lets one person with free device accounts block every join, and the limiter adds state. 10 characters (about 49.5 bits) make guessing infeasible with no moving parts.
- Design 2's preview endpoint: an extra endpoint and another code oracle. Join is idempotent, the board appears right after joining, and leaving is one tap.
- Design 2's per-group nickname column: the profile display_name already exists. The group view hints at setting it in Profile instead.
- Design 2's pre-session InviteLanding with user-agent sniffing, and Design 3's /game/join/:code page: a deep link on iPhone opens Safari's separate account, and Design 3's page creates a throwaway account through SessionGate. A share text with the code and steps, plus a browser-tab warning, covers the case more simply.
- Design 3's background executor, REQUIRES_NEW templates, service methods with no transaction, polling and the refreshing flag in LolPlayerResponse: a large, risky refactor that breaks track() atomicity. Polling keeps the free Render instance awake, which conflicts with the wake-up work. The row claim plus the per-view cap get most of the benefit in about 20 lines.
- Design 3's storing of each downloaded match for every known participant: a real saving for friends who play together, but it adds a cross-player write path that the fake client (one participant per match) cannot exercise. Revisit if the budget becomes tight.
- Design 3's per-user Riot lookup quota: it changes the existing follow endpoint for an exposure that already exists today and is acceptable among friends.
- Design 1's and Design 3's insertIfAbsent native insert with SpEL parameters: no longer needed once only the lock holder writes a player's matches. It also avoids the open question of how Spring Data JPA 4 and Hibernate 7 bind null in native queries.
- Design 2's owner role column with a partial unique index, and Design 3's owner_id with hand-over: the derived owner (earliest member) needs no transfer logic and cannot dangle when a user row disappears.
- Design 3's DELETE group endpoint and unlink endpoint: the last member leaving deletes the group, 'change account' fixes a wrong link, and unlinked is already the default state.
- Design 2's InviteAttemptLimiter and refresh-guard in-memory sets: replaced by the entropy argument and the database row claim.
- Keeping the selected view only in component state (Design 1): the view would reset when coming back from a player page, so it moves to ?view= in the URL. localStorage (Designs 2 and 3) is not needed either.

## openQuestions
- Should a group board also show friends who never install LiftLog, as guest Riot accounts that any member can add? This design assumes no: the board is members' own accounts only. Guests could be added later as a separate table without breaking existing responses.
- Is one League group per user enough, meaning your whole friend circle is in one group? Or does anyone need to be in two groups? This design assumes one. Changing it later changes the API paths, so it is best decided before building.
