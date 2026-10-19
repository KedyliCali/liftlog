-- More numbers per match line for the detailed League pages. Nullable: rows stored before this migration do
-- not have them, and a refresh fills them in over time (gold_earned IS NULL marks a row still to re-download).
ALTER TABLE lol_match_players
    ADD COLUMN gold_earned        INTEGER,
    ADD COLUMN vision_score       INTEGER,
    ADD COLUMN champ_level        INTEGER,
    ADD COLUMN damage_taken       INTEGER,
    -- Fractions 0..1 from Riot's "challenges"; absent in some game modes
    ADD COLUMN kill_participation NUMERIC(5, 4),
    ADD COLUMN damage_share       NUMERIC(5, 4),
    ADD COLUMN largest_multi_kill INTEGER,
    ADD COLUMN wards_placed       INTEGER,
    -- 7 item ids, comma separated, 0 for an empty slot: only ever shown, never queried
    ADD COLUMN items              VARCHAR(64),
    ADD COLUMN summoner_spells    VARCHAR(16),
    ADD COLUMN game_mode          VARCHAR(16);

-- Solo/duo rank over time. A row is added on refresh only when tier, division or LP changed, so a quiet player
-- does not fill the table; the service keeps at most 200 rows per player.
CREATE TABLE lol_rank_history (
    puuid       VARCHAR(100) NOT NULL REFERENCES lol_players(puuid) ON DELETE CASCADE,
    recorded_at TIMESTAMP    NOT NULL,
    tier        VARCHAR(16)  NOT NULL,
    division    VARCHAR(4)   NOT NULL,
    lp          INTEGER      NOT NULL,
    wins        INTEGER      NOT NULL,
    losses      INTEGER      NOT NULL,
    PRIMARY KEY (puuid, recorded_at)
);
