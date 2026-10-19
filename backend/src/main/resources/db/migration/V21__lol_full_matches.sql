-- Whole matches, not just the followed player's line: both teams (objectives, bans) and all ten players, so
-- duos, lane matchups and full scoreboards can be shown. Shared like lol_match_players: a match two followed
-- players were in is stored once. lol_match_players stays as it is; a stored line whose match has no row here is
-- re-downloaded by the refresh backfill, newest first.

CREATE TABLE lol_matches (
    match_id     VARCHAR(30) PRIMARY KEY,
    platform     VARCHAR(8)  NOT NULL,
    queue_id     INTEGER     NOT NULL,
    game_mode    VARCHAR(16),
    game_version VARCHAR(24),            -- "14.20.123.4567"; the web uses major.minor for patch grouping
    ended_at     TIMESTAMP   NOT NULL,
    duration_s   INTEGER     NOT NULL,
    timeline_fetched BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE lol_match_teams (
    match_id     VARCHAR(30) NOT NULL REFERENCES lol_matches(match_id) ON DELETE CASCADE,
    team_id      INTEGER     NOT NULL,   -- 100 blue, 200 red
    win          BOOLEAN     NOT NULL,
    kills        INTEGER     NOT NULL,
    towers       INTEGER     NOT NULL,
    inhibitors   INTEGER     NOT NULL,
    dragons      INTEGER     NOT NULL,
    barons       INTEGER     NOT NULL,
    heralds      INTEGER     NOT NULL,
    grubs        INTEGER     NOT NULL,   -- objectives.horde
    atakhan      INTEGER     NOT NULL DEFAULT 0,
    first_blood  BOOLEAN     NOT NULL,
    first_tower  BOOLEAN     NOT NULL,
    first_dragon BOOLEAN     NOT NULL,
    first_baron  BOOLEAN     NOT NULL,
    bans         VARCHAR(64),            -- champion keys (numeric ids), comma separated, -1 = no ban
    PRIMARY KEY (match_id, team_id)
);

CREATE TABLE lol_match_participants (
    match_id         VARCHAR(30)  NOT NULL REFERENCES lol_matches(match_id) ON DELETE CASCADE,
    participant_id   INTEGER      NOT NULL,   -- 1..10, Riot's; the timeline uses the same ids
    puuid            VARCHAR(100) NOT NULL,
    riot_name        VARCHAR(32),
    riot_tag         VARCHAR(10),
    team_id          INTEGER      NOT NULL,
    champion         VARCHAR(40)  NOT NULL,
    champ_level      INTEGER      NOT NULL,
    position         VARCHAR(16),
    kills INTEGER NOT NULL, deaths INTEGER NOT NULL, assists INTEGER NOT NULL,
    cs               INTEGER      NOT NULL,
    gold             INTEGER      NOT NULL,
    damage           INTEGER      NOT NULL,   -- to champions
    damage_taken     INTEGER      NOT NULL,
    damage_objectives INTEGER     NOT NULL,
    damage_buildings INTEGER      NOT NULL,
    healing          INTEGER      NOT NULL,   -- totalHeal
    shielding        INTEGER      NOT NULL,   -- totalDamageShieldedOnTeammates
    cc_time          INTEGER      NOT NULL,   -- timeCCingOthers (s)
    time_dead        INTEGER      NOT NULL,   -- totalTimeSpentDead (s)
    vision_score     INTEGER      NOT NULL,
    wards_placed     INTEGER      NOT NULL,
    wards_killed     INTEGER      NOT NULL,
    control_wards    INTEGER      NOT NULL,   -- visionWardsBoughtInGame
    double_kills INTEGER NOT NULL, triple_kills INTEGER NOT NULL, quadra_kills INTEGER NOT NULL, penta_kills INTEGER NOT NULL,
    first_blood      BOOLEAN      NOT NULL,   -- firstBloodKill || firstBloodAssist
    solo_kills       INTEGER,                 -- challenges.soloKills, absent in some modes
    kill_participation NUMERIC(5,4),
    damage_share     NUMERIC(5,4),
    items            VARCHAR(64),             -- 7 ids, comma separated, 0 = empty, last is trinket
    summoner_spells  VARCHAR(16),
    keystone         INTEGER,                 -- perks.styles[0].selections[0].perk
    primary_style    INTEGER,
    secondary_style  INTEGER,
    win              BOOLEAN      NOT NULL,
    PRIMARY KEY (match_id, participant_id)
);
CREATE INDEX idx_lol_match_participants_puuid ON lol_match_participants (puuid);

-- Per-minute gold / xp / cs per participant, from the timeline (fetched lazily, when a match detail is opened)
CREATE TABLE lol_match_frames (
    match_id       VARCHAR(30) NOT NULL REFERENCES lol_matches(match_id) ON DELETE CASCADE,
    minute         INTEGER     NOT NULL,
    participant_id INTEGER     NOT NULL,
    gold INTEGER NOT NULL, xp INTEGER NOT NULL, cs INTEGER NOT NULL, level INTEGER NOT NULL,
    PRIMARY KEY (match_id, minute, participant_id)
);
