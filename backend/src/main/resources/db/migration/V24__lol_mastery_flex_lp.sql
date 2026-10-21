-- Champion mastery, flex rank and estimated LP change per match.

-- Ranked flex, stored like solo/duo; NULL while unranked in flex. The mastery totals are summed over every
-- champion Riot returns (lol_champion_mastery keeps only the top ones), and mastery_refreshed_at lets a refresh skip
-- the mastery request when nothing was played since.
ALTER TABLE lol_players
    ADD COLUMN flex_tier            VARCHAR(16),
    ADD COLUMN flex_division        VARCHAR(4),
    ADD COLUMN flex_lp              INTEGER,
    ADD COLUMN flex_wins            INTEGER,
    ADD COLUMN flex_losses          INTEGER,
    ADD COLUMN mastery_score        INTEGER,
    ADD COLUMN mastery_points       BIGINT,
    ADD COLUMN mastery_refreshed_at TIMESTAMP;

-- A player's most played champions by mastery points (top 50), replaced on each mastery fetch
CREATE TABLE lol_champion_mastery (
    puuid           VARCHAR(100) NOT NULL REFERENCES lol_players(puuid) ON DELETE CASCADE,
    champion_id     INTEGER      NOT NULL,   -- Data Dragon champion "key" (numeric)
    champion_level  INTEGER      NOT NULL,
    champion_points BIGINT       NOT NULL,
    last_play_time  TIMESTAMP,
    PRIMARY KEY (puuid, champion_id)
);

-- Rank history now covers both ranked queues; every existing row is solo/duo. Solo and flex snapshots of one
-- refresh share their timestamp, so the queue joins the key.
ALTER TABLE lol_rank_history ADD COLUMN queue VARCHAR(8) NOT NULL DEFAULT 'SOLO';
ALTER TABLE lol_rank_history DROP CONSTRAINT lol_rank_history_pkey;
ALTER TABLE lol_rank_history ADD PRIMARY KEY (puuid, queue, recorded_at);

-- LP won or lost in this match, estimated from two rank snapshots with exactly this one ranked game between them;
-- NULL when it cannot be told
ALTER TABLE lol_match_players ADD COLUMN lp_change INTEGER;
