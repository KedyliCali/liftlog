# League: deep stats (duos, full scoreboards, tables)

Behlül asked (2026-10-19) for the League part to "show everything": duos, much more detailed stats, data and
tables. Until now only the followed player's own line of each match was stored, at most the last 20 matches, so
teammates (duos), opponents (matchups), team objectives and bans were unknown. This adds them.

## What changes
1. **Every match is stored whole**: both teams (objectives, bans) and all 10 players, not just the followed one.
2. **History grows to 100 matches** per player (was 20): the match list request asks Riot for 100 ids (still one
   request); at most 10 matches are downloaded per refresh as before, so history fills in over a few refreshes.
3. **Stats can be filtered** by queue (all / solo / flex / ARAM / other) and window (last 20 / 50 / 100 games).
4. **Match detail page**: full scoreboard of both teams, objectives, bans, and (lazily) the gold graph and 10/15
   minute lane differences from Riot's timeline.

## Backend

### Migration V21 (`V21__lol_full_matches.sql`)
(V19 is unused on purpose; the next free number above V20.)

```sql
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
```

`lol_match_players` stays as it is (the per-followed-player line the existing pages and group table use). A
stored match whose id has no `lol_matches` row is re-downloaded by the existing backfill budget (same pattern as
V14's `gold_earned IS NULL`), newest first, so old matches gain teammates over a few refreshes.

### Riot client
- `Match` gains `gameVersion`, `teams` (`teamId, win, bans[{championId}], objectives{baron, champion, dragon,
  horde, inhibitor, riftHerald, tower, atakhan → {first, kills}}`), and `Participant` gains the fields above
  (`participantId, riotIdGameName, riotIdTagline, teamId, totalDamageTaken, damageDealtToObjectives,
  damageDealtToBuildings, totalHeal, totalDamageShieldedOnTeammates, timeCCingOthers, totalTimeSpentDead,
  wardsKilled, visionWardsBoughtInGame, doubleKills..pentaKills, firstBloodKill, firstBloodAssist,
  challenges.soloKills, perks.styles`). All new numbers nullable (missing → 0 when stored, except solo_kills / kp /
  damage share which stay null).
- New `Optional<Timeline> timeline(platform, matchId)` → `/lol/match/v5/matches/{id}/timeline`, mapped to
  `frames[{timestamp, participantFrames{"1": {totalGold, xp, minionsKilled, jungleMinionsKilled, level}}}]`.
- `FakeRiotClient` builds full 10-player matches (with a repeating duo teammate across matches, so duos show up
  locally), teams with objectives and bans, and a timeline.
- `MATCH_IDS_REQUESTED = 100`, `MATCHES_KEPT = 100` (the per-player list and all stats use at most this many).

### Endpoints (all authenticated; data is public game data so any signed-in user can read any stored match)
1. `GET /api/v1/lol/players/{puuid}/stats?queue=ALL|SOLO|FLEX|ARAM|OTHER&games=20|50|100&tzOffsetMinutes=180`
   → `LolDeepStats` (below). 400 for other values. Queue ids: SOLO 420, FLEX 440, ARAM 450, OTHER everything else.
2. `GET /api/v1/lol/matches/{matchId}` → `LolMatchDetail` (404 if not stored).
3. `GET /api/v1/lol/matches/{matchId}/timeline` → `LolTimeline`; fetched from Riot on first request (one request
   through the rate limiter), then served from `lol_match_frames`. 503 with the usual Riot error body when Riot
   is unavailable; 404 when Riot no longer has it.
4. `GET /api/v1/lol/group/duos` → pairs of group members who played in the same match on the same team:
   `[{a: {userId, displayName}, b: {...}, games, wins}]`, most games first; 204 when not in a group.
5. The existing `GET /api/v1/lol/players/{puuid}` keeps its shape; its `matches` list now has up to 100 rows and
   each `LolMatchResponse` gains `teamId`.

### `LolDeepStats`
```
{
  queue, games (requested window), gamesFound, gamesWithFullData, wins,
  overview: {
    kda, avgKills, avgDeaths, avgAssists, killParticipation, damageShare,
    csPerMinute, goldPerMinute, damagePerMinute, damageTakenPerMinute, visionPerMinute,
    avgWardsPlaced, avgWardsKilled, avgControlWards, avgSoloKills, avgCcTime, deadShare (time dead / game time),
    damagePerGold, objectiveDamagePerMinute, firstBloodRate,
    multiKills: {double, triple, quadra, penta},
    avgGameMinutes
  },
  champions: [{champion, games, wins, kills, deaths, assists (averages), kda, csPerMinute, damagePerMinute,
               goldPerMinute, visionPerMinute, killParticipation, damageShare, pentas, lastPlayed}],
  roles: [{position, games, wins, kda, csPerMinute, damagePerMinute, visionPerMinute}],
  duos: [{puuid, riotName, riotTag, games, wins, kda (theirs together), myKda (mine in those games),
          champions: [top 3 of theirs], tracked: boolean}],          // teammates seen in >= 2 games
  matchups: [{champion (lane opponent), games, wins, kda, avgGoldDiff (end of game, mine − theirs), avgCsDiff}],
  enemyChampions: [{champion, games, wins}],    // every enemy champion faced, min 2 games
  allyChampions:  [{champion, games, wins}],    // teammates' champions, min 2 games
  sides: [{teamId, games, wins}],
  durations: [{label: "<25"|"25-30"|"30-35"|"35+", games, wins}],
  hours: [{label: "00-06"|"06-12"|"12-18"|"18-24", games, wins}],  // local time via tzOffsetMinutes
  weekdays: [{day: 1..7 (Mon..Sun), games, wins}],
  objectives: { avgTeamDragons, avgTeamBarons, avgTeamTowers, avgTeamGrubs, avgTeamHeralds,
                firstBlood: {games, wins}, firstTower: {games, wins}, firstDragon: {games, wins},
                firstBaron: {games, wins} },        // games where my team got it first, and how many were won
  items: [{itemId, games, wins}],         // final item slots 0..5, top 20 by games; the web drops consumables
  keystones: [{keystone, primaryStyle, games, wins}],
  spells: [{spells: "4,14", games, wins}],
  patches: [{patch: "14.20", games, wins}],
  trend: [{matchId, endedAt, win, kda, csPerMinute, damagePerMinute, goldPerMinute, visionPerMinute,
           killParticipation}]           // oldest first, for the trend charts
}
```
Old matches without a `lol_matches` row count in what `lol_match_players` has (overview, champions, roles) and
are left out of what needs teammates (duos, matchups, enemy/ally champions, sides, objectives, keystones, patches);
`gamesWithFullData` is returned too so the page can say "duo stats over 34 of 50 games".
Lane opponent = enemy participant with the same `position` (none for ARAM / no position).

### `LolMatchDetail`
```
{ matchId, queueId, gameMode, gameVersion, endedAt, durationS,
  teams: [{teamId, win, kills, towers, inhibitors, dragons, barons, heralds, grubs, atakhan,
           firstBlood, firstTower, firstDragon, firstBaron, bans: [championKey]}],
  participants: [{participantId, puuid, riotName, riotTag, teamId, champion, champLevel, position, kills, deaths,
                  assists, cs, gold, damage, damageTaken, damageObjectives, damageBuildings, healing, shielding,
                  ccTime, timeDead, visionScore, wardsPlaced, wardsKilled, controlWards, doubleKills, tripleKills,
                  quadraKills, pentaKills, firstBlood, soloKills, killParticipation, damageShare, items: [7],
                  spells: [2], keystone, primaryStyle, secondaryStyle, win, tracked: boolean}],
  timelineAvailable: boolean }
```

### `LolTimeline`
```
{ matchId,
  minutes: [{minute, blueGold, redGold}],                // team gold per minute, for the gold-lead graph
  participants: [{participantId, at10: {gold, cs, xp, level} | null, at15: {...} | null,
                  gold: [per minute]}] }
```

## Web
- **Player page** (`/game/player/:puuid`) becomes tabbed with a sticky filter bar at the top (queue chips: Hepsi /
  Solo / Flex / ARAM; window chips: 20 / 50 / 100):
  - **Genel**: hero (unchanged), stat tile grid (all overview numbers, grouped: Savaş, Farm & altın, Görüş,
    Çoklu öldürme), form, LP chart, trend charts (KDA, CS/dk, hasar/dk, altın/dk; one chart with metric chips),
    side / duration / time-of-day / weekday win-rate bars, objectives table.
  - **Şampiyonlar**: sortable table, every column in `champions`, horizontal scroll, sticky first column.
  - **Duo**: sortable table of teammates (name#tag, games, W/L, WR, their KDA, my KDA with them, their top champs),
    tap → their player page if followed, otherwise a "Takip et" action. Group duos section when in a group.
  - **Rakipler**: lane matchups table + enemy champions + ally champions tables.
  - **Build**: most built items (icons), keystones (rune icons from Data Dragon `runesReforged.json`), spells,
    patches.
  - **Maçlar**: the match list (up to 100), each row taps to the match detail page.
- **Match detail page** (`/game/match/:matchId`): header (result, queue, duration, date, patch), team objective
  strip, bans, two scoreboard tables (one per team; columns: champion+level, name, KDA, CS, gold, damage with a
  bar, vision, items, spells/keystone), a "Detay" toggle with every remaining number, and the gold-lead graph +
  10/15-minute lane differences (loaded from the timeline endpoint on open).
- Every table uses `SortableTable` (extended for sticky first column + horizontal scroll); empty states for
  "not enough games yet" and the "34 of 50 games have full data" note.
- Turkish UI strings via i18n; layered, not flat: cards keep the existing lol styling.
