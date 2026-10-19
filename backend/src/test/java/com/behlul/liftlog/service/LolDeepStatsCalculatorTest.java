package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolMatchDtos.BucketStats;
import com.behlul.liftlog.dto.LolMatchDtos.ChampionGames;
import com.behlul.liftlog.dto.LolMatchDtos.DuoStats;
import com.behlul.liftlog.dto.LolMatchDtos.LolDeepStats;
import com.behlul.liftlog.dto.LolMatchDtos.MatchupStats;
import com.behlul.liftlog.dto.LolMatchDtos.PatchStats;
import com.behlul.liftlog.dto.LolMatchDtos.SideStats;
import com.behlul.liftlog.dto.LolMatchDtos.WeekdayStats;
import com.behlul.liftlog.entity.LolMatch;
import com.behlul.liftlog.entity.LolMatchParticipant;
import com.behlul.liftlog.entity.LolMatchPlayer;
import com.behlul.liftlog.entity.LolMatchTeam;
import com.behlul.liftlog.service.LolDeepStatsCalculator.Game;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LolDeepStatsCalculatorTest {

    private static final String ME = "me";
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 14, 12, 0);

    private static int nextId = 0;

    /** The followed player's line, without the detail columns unless {@link #details} adds them. */
    private static LolMatchPlayer line(String champion, String position, int queueId, int kills, int deaths, int assists,
                                       int cs, int seconds, boolean win, LocalDateTime endedAt) {
        return new LolMatchPlayer("TR1_" + nextId++, ME, queueId, endedAt, seconds, champion, position, kills, deaths,
                assists, cs, 20_000, win);
    }

    private static LolMatchPlayer simple(String champion, boolean win) {
        return line(champion, "MIDDLE", 420, 5, 5, 5, 180, 1_800, win, NOON);
    }

    private static LolMatchPlayer details(LolMatchPlayer m, int gold, String items, String spells) {
        m.fillDetails(new LolMatchPlayer.Details(gold, 30, 15, 18_000, new BigDecimal("0.5000"), new BigDecimal("0.2500"),
                2, 10, items, spells, "CLASSIC"));
        return m;
    }

    /** Another player in the match; the followed player is built from their line with {@link #mine}. */
    private static LolMatchParticipant player(LolMatchPlayer line, int id, String puuid, int teamId, String champion,
                                              String position, int kills, int deaths, int assists, int cs, int gold) {
        return new LolMatchParticipant(line.getMatchId(), id, puuid, "Name" + puuid, "TR1", teamId, champion, 16,
                position, kills, deaths, assists, cs, gold, 15_000, line.isWin() == (teamId == 100))
                .fill(new LolMatchParticipant.Extra(10_000, 2_000, 1_000, 500, 0, 10, 60, 20, 8, 2, 1, 0, 0, 0, 0,
                        false, 1, null, null, "3006,0,0,0,0,0,3363", "4,14", 8010, 8000, 8100));
    }

    /** The followed player's participant row, on blue (100). */
    private static LolMatchParticipant mine(LolMatchPlayer line, int gold, int wardsKilled, int timeDead, boolean firstBlood,
                                            int pentas) {
        return new LolMatchParticipant(line.getMatchId(), 1, ME, "Me", "TR1", 100, line.getChampion(), 16,
                line.getPosition(), line.getKills(), line.getDeaths(), line.getAssists(), line.getCs(), gold,
                line.getDamage(), line.isWin())
                .fill(new LolMatchParticipant.Extra(10_000, 3_000, 2_000, 500, 0, 20, timeDead, 25, 9, wardsKilled, 3,
                        2, 1, 0, pentas, firstBlood, 2, null, null, "3006,0,0,0,0,0,3363", "4,14", 8112, 8100, 8200));
    }

    private static LolMatchTeam team(LolMatchPlayer line, int teamId, boolean firstTower, int dragons) {
        boolean won = line.isWin() == (teamId == 100);
        return new LolMatchTeam(line.getMatchId(), teamId, won, 20, 7, 1, dragons, 1, 1, 3, 0, teamId == 100,
                firstTower, false, false, "266,84,-1,12,53");
    }

    /** A whole game: me on blue with the given others, both teams, on patch 14.20 unless given. */
    private static Game whole(LolMatchPlayer line, String version, LolMatchParticipant... others) {
        List<LolMatchParticipant> participants = new ArrayList<>();
        participants.add(mine(line, 11_000, 4, 120, true, 0));
        participants.addAll(List.of(others));
        LolMatch match = new LolMatch(line.getMatchId(), "TR1", line.getQueueId(), "CLASSIC", version,
                line.getEndedAt(), line.getDurationS());
        return new Game(line, match, List.of(team(line, 100, true, 3), team(line, 200, false, 1)), participants);
    }

    private static Game whole(LolMatchPlayer line, LolMatchParticipant... others) {
        return whole(line, "14.20.612.3456", others);
    }

    private static LolDeepStats calculate(List<Game> games) {
        return LolDeepStatsCalculator.calculate(games, LolQueueFilter.ALL, 20, 0, Set.of());
    }

    @Test
    void theWindowIsTheNewestGamesOfTheQueue() {
        LolMatchPlayer solo1 = line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_800, true, NOON);
        LolMatchPlayer aram = line("Lux", null, 450, 1, 1, 1, 100, 1_200, true, NOON.minusHours(1));
        LolMatchPlayer solo2 = line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_800, true, NOON.minusHours(2));
        LolMatchPlayer normal = line("Ahri", "MIDDLE", 400, 1, 1, 1, 100, 1_800, true, NOON.minusHours(3));
        LolMatchPlayer solo3 = line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_800, true, NOON.minusHours(4));
        List<LolMatchPlayer> newestFirst = List.of(solo1, aram, solo2, normal, solo3);

        assertThat(LolDeepStatsCalculator.select(newestFirst, LolQueueFilter.SOLO, 2)).containsExactly(solo1, solo2);
        assertThat(LolDeepStatsCalculator.select(newestFirst, LolQueueFilter.ARAM, 20)).containsExactly(aram);
        assertThat(LolDeepStatsCalculator.select(newestFirst, LolQueueFilter.OTHER, 20)).containsExactly(normal);
        assertThat(LolDeepStatsCalculator.select(newestFirst, LolQueueFilter.FLEX, 20)).isEmpty();
        assertThat(LolDeepStatsCalculator.select(newestFirst, LolQueueFilter.ALL, 4)).hasSize(4);
    }

    @Test
    void aDuoIsATeammateOfAtLeastTwoGames() {
        LolMatchPlayer first = simple("Ahri", true);
        LolMatchPlayer second = simple("Ahri", false);
        LolMatchPlayer third = simple("Ahri", true);
        List<Game> games = List.of(
                whole(first, player(first, 2, "duo", 100, "Thresh", "UTILITY", 1, 2, 10, 30, 8_000),
                        player(first, 3, "once", 100, "Jinx", "BOTTOM", 5, 1, 5, 200, 12_000),
                        player(first, 6, "foe", 200, "Zed", "MIDDLE", 4, 4, 4, 170, 10_000)),
                whole(second, player(second, 2, "duo", 100, "Leona", "UTILITY", 3, 6, 6, 30, 7_000),
                        player(second, 6, "foe", 200, "Zed", "MIDDLE", 9, 1, 2, 190, 13_000)),
                whole(third, player(third, 2, "duo", 100, "Thresh", "UTILITY", 0, 1, 15, 25, 7_500)));

        LolDeepStats stats = LolDeepStatsCalculator.calculate(games, LolQueueFilter.ALL, 20, 0, Set.of("duo"));

        // "once" played with me a single time, and "foe" twice but against me
        assertThat(stats.duos()).singleElement().satisfies(duo -> {
            assertThat(duo.puuid()).isEqualTo("duo");
            assertThat(duo.riotName()).isEqualTo("Nameduo");
            assertThat(duo.games()).isEqualTo(3);
            assertThat(duo.wins()).isEqualTo(2);
            // Theirs: (4 kills + 31 assists) / 9 deaths; mine: (15 + 15) / 15
            assertThat(duo.kda()).isEqualByComparingTo("3.89");
            assertThat(duo.myKda()).isEqualByComparingTo("2");
            assertThat(duo.champions()).containsExactly("Thresh", "Leona");
            assertThat(duo.tracked()).isTrue();
        });
        assertThat(stats.allyChampions()).extracting(ChampionGames::champion, ChampionGames::games, ChampionGames::wins)
                .containsExactly(tuple("Thresh", 2, 2));
        assertThat(stats.enemyChampions()).extracting(ChampionGames::champion, ChampionGames::games, ChampionGames::wins)
                .containsExactly(tuple("Zed", 2, 1));
    }

    @Test
    void matchupsAreAgainstTheEnemyInMyPosition() {
        LolMatchPlayer first = simple("Ahri", true);
        LolMatchPlayer second = simple("Ahri", false);
        LolMatchPlayer aram = line("Lux", null, 450, 5, 5, 5, 50, 1_200, true, NOON);
        List<Game> games = List.of(
                // My gold is 11 000 and my cs 180 in every game
                whole(first, player(first, 6, "a", 200, "Zed", "MIDDLE", 4, 4, 4, 170, 10_000),
                        player(first, 7, "b", 200, "Garen", "TOP", 4, 4, 4, 170, 10_000)),
                whole(second, player(second, 6, "c", 200, "Zed", "MIDDLE", 9, 1, 2, 200, 13_000)),
                whole(aram, player(aram, 6, "d", 200, "Jinx", null, 9, 1, 2, 200, 13_000)));

        LolDeepStats stats = calculate(games);

        assertThat(stats.matchups()).singleElement().satisfies(m -> {
            assertThat(m.champion()).isEqualTo("Zed");
            assertThat(m.games()).isEqualTo(2);
            assertThat(m.wins()).isEqualTo(1);
            // (+1 000 - 2 000) / 2 and (+10 - 20) / 2
            assertThat(m.avgGoldDiff()).isEqualByComparingTo("-500");
            assertThat(m.avgCsDiff()).isEqualByComparingTo("-5");
            assertThat(m.kda()).isEqualByComparingTo("2");
        });
    }

    @Test
    void oldRowsCountWhereTheirLineIsEnoughButNotInTeammateStats() {
        LolMatchPlayer wholeLine = details(line("Ahri", "MIDDLE", 420, 10, 2, 10, 200, 1_800, true, NOON),
                12_000, "3006,3031,3031,0,0,0,3363", "4,14");
        LolMatchPlayer oldLine = line("Ahri", "MIDDLE", 420, 2, 4, 6, 160, 1_200, false, NOON.minusDays(1));
        List<Game> games = List.of(
                whole(wholeLine, player(wholeLine, 2, "duo", 100, "Thresh", "UTILITY", 1, 2, 10, 30, 8_000)),
                Game.lineOnly(oldLine));

        LolDeepStats stats = calculate(games);

        assertThat(stats.gamesFound()).isEqualTo(2);
        assertThat(stats.gamesWithFullData()).isEqualTo(1);
        assertThat(stats.wins()).isEqualTo(1);
        // Line numbers use both games
        assertThat(stats.overview().avgKills()).isEqualByComparingTo("6");
        assertThat(stats.overview().kda()).isEqualByComparingTo("4.67");
        assertThat(stats.overview().csPerMinute()).isEqualByComparingTo("7.2");
        assertThat(stats.overview().avgGameMinutes()).isEqualByComparingTo("25");
        // Gold only the newer line has: 12 000 over 30 minutes
        assertThat(stats.overview().goldPerMinute()).isEqualByComparingTo("400");
        // Whole-match numbers come from the one whole game
        assertThat(stats.overview().avgWardsKilled()).isEqualByComparingTo("4");
        assertThat(stats.overview().deadShare()).isEqualByComparingTo("0.0667");
        assertThat(stats.overview().firstBloodRate()).isEqualByComparingTo("1");
        assertThat(stats.overview().multiKills().doubleKills()).isEqualTo(2);
        assertThat(stats.champions()).singleElement().satisfies(c -> {
            assertThat(c.games()).isEqualTo(2);
            assertThat(c.lastPlayed()).isEqualTo(NOON);
        });
        assertThat(stats.sides()).extracting(SideStats::teamId, SideStats::games).containsExactly(tuple(100, 1), tuple(200, 0));
        assertThat(stats.objectives().avgTeamDragons()).isEqualByComparingTo("3");
        assertThat(stats.objectives().firstTower().games()).isEqualTo(1);
        assertThat(stats.objectives().firstTower().wins()).isEqualTo(1);
        assertThat(stats.keystones()).singleElement().satisfies(k -> assertThat(k.keystone()).isEqualTo(8112));
        // Items counted once per game, the trinket and empty slots left out
        assertThat(stats.items()).extracting(i -> i.itemId()).containsExactly(3006, 3031);
        assertThat(stats.spells()).singleElement().satisfies(s -> assertThat(s.spells()).isEqualTo("4,14"));
        assertThat(stats.trend()).extracting(t -> t.matchId()).containsExactly(oldLine.getMatchId(), wholeLine.getMatchId());
        assertThat(stats.trend().getFirst().goldPerMinute()).isNull();
    }

    @Test
    void anOldPentaCountsFromTheLargestMultiKill() {
        LolMatchPlayer old = line("Jinx", "BOTTOM", 420, 12, 2, 3, 250, 1_900, true, NOON);
        old.fillDetails(new LolMatchPlayer.Details(14_000, 20, 16, 15_000, null, null, 5, 8, null, null, "CLASSIC"));

        assertThat(calculate(List.of(Game.lineOnly(old))).champions().getFirst().pentas()).isEqualTo(1);
    }

    @Test
    void timeOfDayAndWeekdayUseTheViewersOffset() {
        // Sunday 22:30 UTC is Monday 01:30 in Türkiye (UTC+3)
        LocalDateTime sundayLate = LocalDateTime.of(2026, 10, 18, 22, 30);
        LolMatchPlayer game = line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 1_800, true, sundayLate);

        LolDeepStats utc = LolDeepStatsCalculator.calculate(List.of(Game.lineOnly(game)), LolQueueFilter.ALL, 20, 0, Set.of());
        LolDeepStats turkey = LolDeepStatsCalculator.calculate(List.of(Game.lineOnly(game)), LolQueueFilter.ALL, 20, 180, Set.of());

        assertThat(utc.hours()).extracting(BucketStats::label, BucketStats::games)
                .containsExactly(tuple("00-06", 0), tuple("06-12", 0), tuple("12-18", 0), tuple("18-24", 1));
        assertThat(turkey.hours().getFirst().games()).isEqualTo(1);
        assertThat(utc.weekdays()).filteredOn(d -> d.games() > 0).extracting(WeekdayStats::day).containsExactly(7);
        assertThat(turkey.weekdays()).filteredOn(d -> d.games() > 0).extracting(WeekdayStats::day).containsExactly(1);
        assertThat(turkey.weekdays()).hasSize(7);
    }

    @Test
    void gameLengthsFallIntoFiveMinuteBuckets() {
        List<Game> games = List.of(
                Game.lineOnly(line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 25 * 60 - 1, true, NOON)),
                Game.lineOnly(line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 25 * 60, false, NOON)),
                Game.lineOnly(line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 34 * 60 + 59, true, NOON)),
                Game.lineOnly(line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 35 * 60, true, NOON)),
                Game.lineOnly(line("Ahri", "MIDDLE", 420, 1, 1, 1, 100, 50 * 60, false, NOON)));

        assertThat(calculate(games).durations()).extracting(BucketStats::label, BucketStats::games, BucketStats::wins)
                .containsExactly(tuple("<25", 1, 1), tuple("25-30", 1, 0), tuple("30-35", 1, 1), tuple("35+", 2, 1));
    }

    @Test
    void patchesAreNewestFirstComparedAsNumbers() {
        LolMatchPlayer a = simple("Ahri", true);
        LolMatchPlayer b = simple("Ahri", false);
        LolMatchPlayer c = simple("Ahri", true);

        LolDeepStats stats = calculate(List.of(whole(a, "14.9.1.2"), whole(b, "14.10.3.4"), whole(c, "14.10.5.6")));

        assertThat(stats.patches()).extracting(PatchStats::patch, PatchStats::games, PatchStats::wins)
                .containsExactly(tuple("14.10", 2, 1), tuple("14.9", 1, 1));
    }

    @Test
    void anEmptyWindowHasNoNumbersRatherThanZeros() {
        LolDeepStats stats = LolDeepStatsCalculator.calculate(List.of(), LolQueueFilter.FLEX, 50, 0, Set.of());

        assertThat(stats.queue()).isEqualTo("FLEX");
        assertThat(stats.games()).isEqualTo(50);
        assertThat(stats.gamesFound()).isZero();
        assertThat(stats.overview().kda()).isNull();
        assertThat(stats.overview().csPerMinute()).isNull();
        assertThat(stats.overview().firstBloodRate()).isNull();
        assertThat(stats.overview().multiKills().pentaKills()).isZero();
        assertThat(stats.objectives().avgTeamDragons()).isNull();
        assertThat(stats.duos()).isEmpty();
        assertThat(stats.matchups()).extracting(MatchupStats::games).isEmpty();
        assertThat(stats.durations()).hasSize(4).allSatisfy(b -> assertThat(b.games()).isZero());
        assertThat(stats.sides()).hasSize(2);
        assertThat(stats.trend()).isEmpty();
    }

    @Test
    void duosAreSortedByGamesTogether() {
        List<Game> games = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            LolMatchPlayer l = simple("Ahri", true);
            List<LolMatchParticipant> others = new ArrayList<>(List.of(player(l, 2, "often", 100, "Thresh", "UTILITY", 1, 1, 1, 1, 1)));
            if (i < 2) {
                others.add(player(l, 3, "sometimes", 100, "Jinx", "BOTTOM", 1, 1, 1, 1, 1));
            }
            games.add(whole(l, others.toArray(LolMatchParticipant[]::new)));
        }

        assertThat(calculate(games).duos()).extracting(DuoStats::puuid, DuoStats::games, DuoStats::tracked)
                .containsExactly(tuple("often", 3, false), tuple("sometimes", 2, false));
    }
}
