# Detailed League stats: design

Behlül asked (2026-10-19) for more information on the League pages: tables, more statistics, more detail.
Merge **after** the passkey PR (#19): this adds migration V14, and Flyway applies versions in order.

## What the player sees
Player page (`/game/:puuid`), top to bottom; every section works with old matches that lack the new numbers
(they are simply left out of averages that need them):
1. Hero (unchanged) plus a **current streak** chip ("3 galibiyet serisi" / "2 mağlubiyet").
2. **LP chart** (recharts line): solo/duo rank history as one comparable score, with tier names on the axis and
   a dot per change. Hidden until there are at least 2 points.
3. **Stat tiles** (layered tiles, two rows): Kazanma %, KDA, CS/dk, Kill katılımı %, Hasar/dk, Altın/dk,
   Görüş/dk, Hasar payı %. Each with the average and a small "son 20" caption.
4. **Roller**: a bar per position played (Top, Orman, Orta, Nişancı, Destek) with games and win rate.
5. **Şampiyonlar** table: every champion in the stored games (not only the top 3): games, G–M, win %, KDA,
   CS/dk, hasar/dk. Tapping a column header sorts by it.
6. **Kuyruklar**: Solo/Duo, Flex, ARAM, … with games and win rate.
7. **Rekorlar**: most kills, best KDA, most damage, most CS, longest game, largest multikill (each links to the
   match row).
8. **Son maçlar**: today's rows plus item icons (7 slots), summoner spells, KP %, damage, gold, vision, and a
   multikill badge (ÇİFT, ÜÇLÜ, DÖRTLÜ, PENTA). Tapping a row expands it with the full numbers.

Group board: a **Tablo** toggle next to the board shows the members side by side: rank, win % (last 20), KDA,
KP %, CS/dk, hasar/dk, görüş/dk; tapping a header sorts.

## Backend
Migration V14:
- `lol_match_players` gains nullable columns: `gold_earned INT`, `vision_score INT`, `champ_level INT`,
  `damage_taken INT`, `kill_participation NUMERIC(5,4)`, `damage_share NUMERIC(5,4)`, `largest_multi_kill INT`,
  `wards_placed INT`, `items VARCHAR(64)` (7 item ids, comma separated, 0 for empty), `summoner_spells
  VARCHAR(16)` (2 ids), `game_mode VARCHAR(16)`. Nullable because older rows do not have them.
- `lol_rank_history (puuid → lol_players ON DELETE CASCADE, recorded_at TIMESTAMP, tier, division, lp, wins,
  losses, PRIMARY KEY (puuid, recorded_at))`: a row is added on refresh only when tier, division or LP changed
  (or the first time). Keep at most 200 per player.

Riot data: match-v5 participant fields `goldEarned`, `visionScore`, `champLevel`, `totalDamageTaken`,
`largestMultiKill`, `wardsPlaced`, `item0..item6`, `summoner1Id`, `summoner2Id`, info `gameMode`, and
`challenges.killParticipation`, `challenges.teamDamagePercentage` (both 0..1; absent in some modes, then null).

Backfill: a refresh also re-downloads stored matches whose `gold_earned` is null, within the same
`MAX_NEW_MATCHES_PER_REFRESH` budget (new matches first), so details fill in over a few refreshes.

API (shapes added to existing responses; nothing removed):
- `LolMatchResponse` += `gold, vision, champLevel, damageTaken, killParticipation, damageShare,
  largestMultiKill, wardsPlaced, items: int[7] | null, summonerSpells: int[2] | null, gameMode`.
- `LolSummary` += `killParticipation, damagePerMinute, goldPerMinute, visionPerMinute, damageShare` (averages over
  matches that have the value; null when none), `streak: {win: boolean, count}`, `roles: [{position, games,
  wins}]`, `queues: [{queueId, games, wins}]`, `champions` now lists every champion with `games, wins, kda,
  csPerMinute, damagePerMinute`, `records: {mostKills, bestKda, mostDamage, mostCs, longestGame,
  largestMultiKill}` each `{matchId, value}` or null.
- `GET /api/v1/lol/players/{puuid}/rank-history` → `[{recordedAt, tier, division, lp}]` oldest first (same
  visibility rule as the detail page).
- `LolPlayerResponse` += `summary: {kda, killParticipation, csPerMinute, damagePerMinute, visionPerMinute}`
  computed from the stored recent matches (for the group table and the follow list).

## Web
- Data Dragon: item icons `…/cdn/{version}/img/item/{id}.png`, summoner spells need the
  `summoner.json` id→key map (fetch once, cache with the version), fall back to nothing.
- Recharts LineChart for the LP history; ranks become one number with the existing `rankScore`.
- Sortable tables as a small reusable component; Turkish and English labels.
- Style: the sport look (lime #c8ff2e on near-black); no flat single blocks (offset plates, lime edge strips,
  overlapping tags).
