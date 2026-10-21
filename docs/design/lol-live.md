# League: mastery, live games, flex and LP change

Added 2026-10-21. The app runs on a Riot **personal** key: 20 requests/s and 100 per 2 minutes, shared by every
user. Every new Riot call goes through `RiotRateLimiter`, answers are kept (DB or memory) and refreshed lazily.

## Migration V24 (`V24__lol_mastery_flex_lp.sql`)
- `lol_players` + `flex_tier, flex_division, flex_lp, flex_wins, flex_losses` (like solo), `mastery_score`
  (sum of every champion's level), `mastery_points` (sum of points), `mastery_refreshed_at`.
- `lol_champion_mastery (puuid → lol_players, champion_id, champion_level, champion_points, last_play_time)`:
  the top 50 champions by points, replaced on each mastery fetch.
- `lol_rank_history.queue` (`SOLO` | `FLEX`, existing rows `SOLO`); the key becomes `(puuid, queue, recorded_at)`
  because both queues' snapshots of one refresh share a timestamp. 200 rows kept per player and queue.
- `lol_match_players.lp_change` (nullable).

## Riot calls
| Call | Endpoint | When | Cost |
|---|---|---|---|
| Mastery | `champion-mastery-v5 /champion-masteries/by-puuid/{puuid}` (platform host) | in a refresh, only when new matches were found, nothing is stored yet, or the stored copy is a day old | +1 per refresh at most; `REQUESTS_PER_REFRESH` is now 4 + 10 |
| Live game | `spectator-v5 /active-games/by-summoner/{puuid}` (platform host), 404 = not in a game | on demand | 1 per player per minute at most |
| Flex | already in the `league-v4` answer | every refresh | 0 |

## Endpoints
- `GET /players/{puuid}` (unchanged shape plus): `player.flexRank` (like `rank`), `matches[].lpChange`,
  `seasonPeak` and `flexSeasonPeak` (`{recordedAt, tier, division, lp, queue}` or null).
- `GET /players/{puuid}/rank-history?queue=SOLO|FLEX` (SOLO by default; each point now has `queue`).
- `GET /players/{puuid}/mastery` → `{score, totalPoints, refreshedAt, champions: [{championId, level, points,
  lastPlayed}]}`; never calls Riot.
- `GET /players/{puuid}/live` → 204 when not in a game, else `{gameId, gameMode, queueId, gameStartTime (epoch
  ms, 0 while loading), gameLengthS, fetchedAt (epoch ms), participants: [{puuid, riotName, riotTag, teamId,
  championId, spells[2], keystone, primaryStyle, secondaryStyle, rank (solo, only when the player is already
  cached), tracked, bot}], bans: [{championId, teamId}]}`. 503 when Riot cannot be asked and nothing recent is
  cached.
- `GET /group/live` → 204 outside a group, else `{members: [{userId, puuid, inGame (null = not asked), queueId,
  gameMode, gameStartTime, gameLengthS, fetchedAt, championId}]}` for linked members.
- `GET /matches/{matchId}`: each participant gains `lpChange` (followed players only).

## Live cache (`LolLiveService`, in memory)
- One answer per puuid; an answer about a game is stored for **every** participant, so members playing together
  cost one request. Player page: answers up to 60 s old are reused; group board: up to 2 minutes.
- Budget: the player page asks only while more than `RESERVED_FOR_ACTIONS` requests are left (otherwise an answer
  up to 10 minutes old, else 503); the group board asks at most 10 members per view and only while more than
  `REQUESTS_PER_REFRESH + RESERVED_FOR_ACTIONS` are left; the rest are `inGame: null`.
- Ranks in a live game come from `lol_players` only. Asking league-v4 for ten players per view would cost more than
  the whole live feature.
- A restart forgets the cache, which costs at most one request per player looked at.

## LP change per match
Tier + division + LP become one number (`LolRanks.score`): `tier × 400 + division × 100 + LP` below Master
(IRON IV 0 = 0 … DIAMOND I 99 = 2799) and `2800 + LP` for Master, Grandmaster and Challenger (their LP is one
ladder). When a refresh records a new snapshot of a queue, the LP change goes to a game only if:
1. the previous snapshot of that queue counted exactly one game fewer (`wins + losses`),
2. exactly one stored game of that queue (420 / 440) ended after the previous snapshot,
3. its result is the one the counts moved by (wins +1 for a win), and
4. the difference agrees with the result, is not zero and is at most 100 LP (`LolRanks.lpChange`).
Otherwise it stays null. Promotions and demotions are counted across the division line (GOLD IV 90 → GOLD III 15
is +25), since LP carries over.

## Season peak
The highest snapshot of the queue since `liftlog.riot.season-start` (env `LOL_SEASON_START`, default
2026-01-08); the first time it was reached when reached again later. The web shows it only when it is above the
current rank.

## Fake client (`RIOT_FAKE=true`)
- Mastery: 30 champions, points falling off from a main of up to a million, levels following points.
- Live: a Riot ID containing `ingame` is always in a game, one containing `offline` never; anyone else about one
  time in three, changing every 20 minutes. The duo partner plays on the same team.
- Flex: two of three ranked players have a flex entry near their solo rank.
- LP chips do not appear by themselves locally: the fake's match list does not grow, so no game ever falls
  between two snapshots.

## Web
- Player hero: `SOLO` and `FLEX` rank tabs, the season peak, and a red "Canlı 12:34" tag over the top-left
  corner (the clock ticks every second; the query polls every 60 s). A tap opens a sheet with both teams (side
  colored cards: champion, spells, keystone, name, cached rank) and bans.
- New tab **Mastery** (after Champions): mastery score and total points tiles, then the champions with a level
  tag hanging off the icon, a points bar and "last played".
- Match rows and the match page header: `+18 LP` / `−21 LP` chips.
- Group board and table: "Şu an oyunda" tag with the champion icon; the table's Rank column shows Solo and Flex.
