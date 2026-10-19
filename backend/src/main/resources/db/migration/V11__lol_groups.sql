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
    -- group_id leads, so this index also serves "members of group X" and the detail-page visibility check
    CONSTRAINT uq_lol_group_members_account UNIQUE (group_id, puuid)
);
