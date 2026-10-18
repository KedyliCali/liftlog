-- League of Legends tracker. Player and match data is public game data cached from the Riot API, so it
-- is shared: when two friends follow the same player, it is fetched once.
CREATE TABLE lol_players (
    puuid           VARCHAR(100) PRIMARY KEY,
    platform        VARCHAR(8)   NOT NULL,
    game_name       VARCHAR(32)  NOT NULL,
    tag_line        VARCHAR(10)  NOT NULL,
    profile_icon_id INTEGER,
    summoner_level  INTEGER,
    -- Ranked solo/duo; NULL while unranked
    solo_tier       VARCHAR(16),
    solo_division   VARCHAR(4),
    solo_lp         INTEGER,
    solo_wins       INTEGER,
    solo_losses     INTEGER,
    refreshed_at    TIMESTAMP
);

-- Who follows whom; each user keeps their own list
CREATE TABLE lol_tracked_players (
    user_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    puuid    VARCHAR(100) NOT NULL REFERENCES lol_players(puuid) ON DELETE CASCADE,
    added_at TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, puuid)
);

-- One row per match per followed player: only the numbers the app shows, not Riot's whole payload
CREATE TABLE lol_match_players (
    match_id   VARCHAR(30)  NOT NULL,
    puuid      VARCHAR(100) NOT NULL REFERENCES lol_players(puuid) ON DELETE CASCADE,
    queue_id   INTEGER      NOT NULL,
    ended_at   TIMESTAMP    NOT NULL,
    duration_s INTEGER      NOT NULL,
    champion   VARCHAR(40)  NOT NULL,
    position   VARCHAR(16),
    kills      INTEGER      NOT NULL,
    deaths     INTEGER      NOT NULL,
    assists    INTEGER      NOT NULL,
    cs         INTEGER      NOT NULL,
    damage     INTEGER      NOT NULL,
    win        BOOLEAN      NOT NULL,
    PRIMARY KEY (match_id, puuid)
);

CREATE INDEX idx_lol_match_players_recent ON lol_match_players (puuid, ended_at DESC);
