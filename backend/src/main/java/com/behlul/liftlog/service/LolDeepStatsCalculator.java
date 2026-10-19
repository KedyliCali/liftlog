package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos;
import com.behlul.liftlog.dto.LolMatchDtos.BucketStats;
import com.behlul.liftlog.dto.LolMatchDtos.ChampionGames;
import com.behlul.liftlog.dto.LolMatchDtos.DeepChampionStats;
import com.behlul.liftlog.dto.LolMatchDtos.DeepOverview;
import com.behlul.liftlog.dto.LolMatchDtos.DeepRoleStats;
import com.behlul.liftlog.dto.LolMatchDtos.DuoStats;
import com.behlul.liftlog.dto.LolMatchDtos.FirstStats;
import com.behlul.liftlog.dto.LolMatchDtos.ItemStats;
import com.behlul.liftlog.dto.LolMatchDtos.KeystoneStats;
import com.behlul.liftlog.dto.LolMatchDtos.LolDeepStats;
import com.behlul.liftlog.dto.LolMatchDtos.MatchupStats;
import com.behlul.liftlog.dto.LolMatchDtos.MultiKills;
import com.behlul.liftlog.dto.LolMatchDtos.ObjectiveStats;
import com.behlul.liftlog.dto.LolMatchDtos.PatchStats;
import com.behlul.liftlog.dto.LolMatchDtos.SideStats;
import com.behlul.liftlog.dto.LolMatchDtos.SpellStats;
import com.behlul.liftlog.dto.LolMatchDtos.TrendPoint;
import com.behlul.liftlog.dto.LolMatchDtos.WeekdayStats;
import com.behlul.liftlog.entity.LolMatch;
import com.behlul.liftlog.entity.LolMatchParticipant;
import com.behlul.liftlog.entity.LolMatchPlayer;
import com.behlul.liftlog.entity.LolMatchTeam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The detailed stats page's numbers, from a player's stored games. Pure, so it is tested directly; the service
 * only loads rows.
 *
 * <p>A game always has the followed player's own line ({@link LolMatchPlayer}); games downloaded since whole
 * matches are stored also have the match, both teams and all ten players. Everything that needs other players
 * (duos, matchups, enemy and ally champions, sides, objectives) or a number only the whole match has is computed
 * over the whole games only, so a half-backfilled history still gives honest numbers; the rest uses every game.
 */
final class LolDeepStatsCalculator {

    static final int MIN_DUO_GAMES = 2;
    static final int MIN_CHAMPION_GAMES = 2;
    static final int TOP_ITEMS = 20;
    static final int DUO_CHAMPIONS = 3;
    static final List<String> DURATION_LABELS = List.of("<25", "25-30", "30-35", "35+");
    static final List<String> HOUR_LABELS = List.of("00-06", "06-12", "12-18", "18-24");
    private static final int FRACTION_SCALE = 4;
    private static final int SCALE = 2;
    /** The six item slots; the seventh is the trinket, which says nothing about a build. */
    private static final int ITEM_SLOTS = 6;

    private LolDeepStatsCalculator() {
    }

    /**
     * One stored game of the followed player.
     *
     * @param match        null when the match is not stored whole (yet)
     * @param me           the followed player among the participants; null when the match is not stored whole
     */
    record Game(LolMatchPlayer line, LolMatch match, List<LolMatchTeam> teams, List<LolMatchParticipant> participants,
                LolMatchParticipant me) {

        Game(LolMatchPlayer line, LolMatch match, List<LolMatchTeam> teams, List<LolMatchParticipant> participants) {
            this(line, match, teams == null ? List.of() : teams, participants == null ? List.of() : participants,
                    match == null || participants == null ? null : participants.stream()
                            .filter(p -> p.getPuuid().equals(line.getPuuid())).findFirst().orElse(null));
        }

        /** Only the line is stored: a match from before whole matches, waiting for the backfill. */
        static Game lineOnly(LolMatchPlayer line) {
            return new Game(line, null, null, null);
        }

        boolean whole() {
            return me != null;
        }

        LolMatchTeam myTeam() {
            return me == null ? null : teams.stream().filter(t -> t.getTeamId() == me.getTeamId()).findFirst().orElse(null);
        }

        List<LolMatchParticipant> teammates() {
            return participants.stream().filter(p -> p != me && p.getTeamId() == me.getTeamId()).toList();
        }

        List<LolMatchParticipant> enemies() {
            return participants.stream().filter(p -> p.getTeamId() != me.getTeamId()).toList();
        }

        boolean win() {
            return line.isWin();
        }
    }

    /** The window: the newest {@code games} lines in the queue, out of the newest stored ones (newest first). */
    static List<LolMatchPlayer> select(List<LolMatchPlayer> newestFirst, LolQueueFilter queue, int games) {
        return newestFirst.stream().filter(m -> queue.includes(m.getQueueId())).limit(games).toList();
    }

    /**
     * @param newestFirst     the selected games, newest first
     * @param tzOffsetMinutes the viewer's offset from UTC, for the time of day and weekday charts
     * @param trackedPuuids   players the viewer can open, to mark duos that link to a player page
     */
    static LolDeepStats calculate(List<Game> newestFirst, LolQueueFilter queue, int games, int tzOffsetMinutes,
                                  Set<String> trackedPuuids) {
        List<Game> whole = newestFirst.stream().filter(Game::whole).toList();
        return new LolDeepStats(queue.name(), games, newestFirst.size(), whole.size(), wins(newestFirst),
                overview(newestFirst, whole), champions(newestFirst), roles(newestFirst),
                duos(whole, trackedPuuids), matchups(whole), opponentChampions(whole, Game::enemies),
                opponentChampions(whole, Game::teammates), sides(whole), durations(newestFirst),
                hours(newestFirst, tzOffsetMinutes), weekdays(newestFirst, tzOffsetMinutes), objectives(whole),
                items(newestFirst), keystones(whole), spells(newestFirst), patches(whole), trend(newestFirst));
    }

    private static DeepOverview overview(List<Game> games, List<Game> whole) {
        int n = games.size();
        return new DeepOverview(
                n == 0 ? null : kda(games),
                average(games, g -> g.line().getKills()),
                average(games, g -> g.line().getDeaths()),
                average(games, g -> g.line().getAssists()),
                meanFraction(games, g -> g.line().getKillParticipation()),
                meanFraction(games, g -> g.line().getDamageShare()),
                perMinute(games, g -> g.line().getCs()),
                perMinute(games, g -> g.line().getGoldEarned()),
                perMinute(games, g -> g.line().getDamage()),
                perMinute(games, g -> g.line().getDamageTaken()),
                perMinute(games, g -> g.line().getVisionScore()),
                average(games, g -> g.line().getWardsPlaced()),
                average(whole, g -> g.me().getWardsKilled()),
                average(whole, g -> g.me().getControlWards()),
                average(whole, g -> g.me().getSoloKills()),
                average(whole, g -> g.me().getCcTime()),
                share(whole, g -> g.me().getTimeDead(), g -> g.line().getDurationS()),
                damagePerGold(games),
                perMinute(whole, g -> g.me().getDamageObjectives()),
                whole.isEmpty() ? null : fraction(count(whole, g -> g.me().isFirstBlood()), whole.size()),
                new MultiKills(sum(whole, g -> g.me().getDoubleKills()), sum(whole, g -> g.me().getTripleKills()),
                        sum(whole, g -> g.me().getQuadraKills()), sum(whole, g -> g.me().getPentaKills())),
                n == 0 ? null : divide(sum(games, g -> g.line().getDurationS()), 60L * n, SCALE));
    }

    /** Most played first; among equally played, the one won more often. */
    private static List<DeepChampionStats> champions(List<Game> games) {
        return groupBy(games, g -> g.line().getChampion()).entrySet().stream()
                .map(e -> {
                    List<Game> list = e.getValue();
                    return new DeepChampionStats(e.getKey(), list.size(), wins(list),
                            average(list, g -> g.line().getKills()), average(list, g -> g.line().getDeaths()),
                            average(list, g -> g.line().getAssists()), kda(list),
                            perMinute(list, g -> g.line().getCs()), perMinute(list, g -> g.line().getDamage()),
                            perMinute(list, g -> g.line().getGoldEarned()),
                            perMinute(list, g -> g.line().getVisionScore()),
                            meanFraction(list, g -> g.line().getKillParticipation()),
                            meanFraction(list, g -> g.line().getDamageShare()),
                            sum(list, LolDeepStatsCalculator::pentas),
                            // Newest first, so the first game is the last one played
                            list.getFirst().line().getEndedAt());
                })
                .sorted(Comparator.comparingInt(DeepChampionStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(DeepChampionStats::wins).reversed()))
                .toList();
    }

    /** A game not stored whole only knows its largest multi kill: a penta there means at least one. */
    private static int pentas(Game g) {
        if (g.whole()) {
            return g.me().getPentaKills();
        }
        Integer largest = g.line().getLargestMultiKill();
        return largest != null && largest >= 5 ? 1 : 0;
    }

    /** ARAM and some event modes have no position; those games are left out instead of shown as a role. */
    private static List<DeepRoleStats> roles(List<Game> games) {
        List<Game> withPosition = games.stream().filter(g -> g.line().getPosition() != null).toList();
        return groupBy(withPosition, g -> g.line().getPosition()).entrySet().stream()
                .map(e -> {
                    List<Game> list = e.getValue();
                    return new DeepRoleStats(e.getKey(), list.size(), wins(list), kda(list),
                            perMinute(list, g -> g.line().getCs()), perMinute(list, g -> g.line().getDamage()),
                            perMinute(list, g -> g.line().getVisionScore()));
                })
                .sorted(Comparator.comparingInt(DeepRoleStats::games).reversed())
                .toList();
    }

    /** Teammates seen in at least {@link #MIN_DUO_GAMES} games; most games together first. */
    private static List<DuoStats> duos(List<Game> whole, Set<String> trackedPuuids) {
        Map<String, List<Game>> gamesWith = new LinkedHashMap<>();
        Map<String, List<LolMatchParticipant>> theirLines = new LinkedHashMap<>();
        for (Game g : whole) {
            for (LolMatchParticipant mate : g.teammates()) {
                gamesWith.computeIfAbsent(mate.getPuuid(), k -> new ArrayList<>()).add(g);
                theirLines.computeIfAbsent(mate.getPuuid(), k -> new ArrayList<>()).add(mate);
            }
        }
        return gamesWith.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_DUO_GAMES)
                .map(e -> {
                    List<LolMatchParticipant> theirs = theirLines.get(e.getKey());
                    // Newest first, so the first line has their current Riot ID
                    LolMatchParticipant newest = theirs.getFirst();
                    List<String> champions = groupBy(theirs, LolMatchParticipant::getChampion).entrySet().stream()
                            .sorted(Comparator.comparingInt((Map.Entry<String, List<LolMatchParticipant>> c) -> c.getValue().size()).reversed())
                            .limit(DUO_CHAMPIONS).map(Map.Entry::getKey).toList();
                    return new DuoStats(e.getKey(), newest.getRiotName(), newest.getRiotTag(), e.getValue().size(),
                            wins(e.getValue()),
                            LolStats.kda(sumOf(theirs, LolMatchParticipant::getKills),
                                    sumOf(theirs, LolMatchParticipant::getDeaths), sumOf(theirs, LolMatchParticipant::getAssists)),
                            kda(e.getValue()), champions, trackedPuuids.contains(e.getKey()));
                })
                .sorted(Comparator.comparingInt(DuoStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(DuoStats::wins).reversed()))
                .toList();
    }

    /** Lane opponent: the enemy with the same position. Games without a position (ARAM) have none. */
    private static List<MatchupStats> matchups(List<Game> whole) {
        Map<String, List<Game>> byOpponent = new LinkedHashMap<>();
        Map<String, List<LolMatchParticipant>> opponents = new LinkedHashMap<>();
        for (Game g : whole) {
            String position = g.me().getPosition();
            if (position == null) {
                continue;
            }
            g.enemies().stream().filter(p -> position.equals(p.getPosition())).findFirst().ifPresent(opponent -> {
                byOpponent.computeIfAbsent(opponent.getChampion(), k -> new ArrayList<>()).add(g);
                opponents.computeIfAbsent(opponent.getChampion(), k -> new ArrayList<>()).add(opponent);
            });
        }
        return byOpponent.entrySet().stream()
                .map(e -> {
                    List<Game> list = e.getValue();
                    List<LolMatchParticipant> theirs = opponents.get(e.getKey());
                    long goldDiff = 0;
                    long csDiff = 0;
                    for (int i = 0; i < list.size(); i++) {
                        goldDiff += list.get(i).me().getGold() - theirs.get(i).getGold();
                        csDiff += list.get(i).me().getCs() - theirs.get(i).getCs();
                    }
                    return new MatchupStats(e.getKey(), list.size(), wins(list), kda(list),
                            divide(goldDiff, list.size(), SCALE), divide(csDiff, list.size(), SCALE));
                })
                .sorted(Comparator.comparingInt(MatchupStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(MatchupStats::wins).reversed()))
                .toList();
    }

    /** Champions among the enemies (or teammates) of at least {@link #MIN_CHAMPION_GAMES} games. */
    private static List<ChampionGames> opponentChampions(List<Game> whole,
                                                         Function<Game, List<LolMatchParticipant>> others) {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (Game g : whole) {
            // A champion appears at most once per team, but count each game once in any case
            Set<String> seen = new LinkedHashSet<>();
            others.apply(g).forEach(p -> seen.add(p.getChampion()));
            seen.forEach(champion -> tallies.computeIfAbsent(champion, k -> new Tally()).add(g.win()));
        }
        return tallies.entrySet().stream()
                .filter(e -> e.getValue().games >= MIN_CHAMPION_GAMES)
                .map(e -> new ChampionGames(e.getKey(), e.getValue().games, e.getValue().wins))
                .sorted(Comparator.comparingInt(ChampionGames::games).reversed()
                        .thenComparing(Comparator.comparingInt(ChampionGames::wins).reversed()))
                .toList();
    }

    /** Always both sides, blue first, so the chart keeps its shape with no games on one of them. */
    private static List<SideStats> sides(List<Game> whole) {
        return List.of(100, 200).stream()
                .map(teamId -> {
                    List<Game> list = whole.stream().filter(g -> g.me().getTeamId() == teamId).toList();
                    return new SideStats(teamId, list.size(), wins(list));
                })
                .toList();
    }

    private static List<BucketStats> durations(List<Game> games) {
        return buckets(games, DURATION_LABELS, g -> {
            int minutes = g.line().getDurationS() / 60;
            return minutes < 25 ? 0 : minutes < 30 ? 1 : minutes < 35 ? 2 : 3;
        });
    }

    /** By the hour the game ended, in the viewer's time zone (stored times are UTC). */
    private static List<BucketStats> hours(List<Game> games, int tzOffsetMinutes) {
        return buckets(games, HOUR_LABELS, g -> local(g, tzOffsetMinutes).getHour() / 6);
    }

    private static List<WeekdayStats> weekdays(List<Game> games, int tzOffsetMinutes) {
        Tally[] days = tallies(7);
        games.forEach(g -> days[local(g, tzOffsetMinutes).getDayOfWeek().getValue() - 1].add(g.win()));
        List<WeekdayStats> result = new ArrayList<>();
        for (int i = 0; i < days.length; i++) {
            result.add(new WeekdayStats(i + 1, days[i].games, days[i].wins));
        }
        return result;
    }

    private static LocalDateTime local(Game g, int tzOffsetMinutes) {
        return g.line().getEndedAt().plusMinutes(tzOffsetMinutes);
    }

    /** Every label, in order, even when it has no games, so the bars keep their places. */
    private static List<BucketStats> buckets(List<Game> games, List<String> labels, ToIntFunction<Game> bucket) {
        Tally[] tallies = tallies(labels.size());
        games.forEach(g -> tallies[bucket.applyAsInt(g)].add(g.win()));
        List<BucketStats> result = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            result.add(new BucketStats(labels.get(i), tallies[i].games, tallies[i].wins));
        }
        return result;
    }

    private static ObjectiveStats objectives(List<Game> whole) {
        List<Game> withTeam = whole.stream().filter(g -> g.myTeam() != null).toList();
        return new ObjectiveStats(
                average(withTeam, g -> g.myTeam().getDragons()),
                average(withTeam, g -> g.myTeam().getBarons()),
                average(withTeam, g -> g.myTeam().getTowers()),
                average(withTeam, g -> g.myTeam().getGrubs()),
                average(withTeam, g -> g.myTeam().getHeralds()),
                first(withTeam, LolMatchTeam::isFirstBlood),
                first(withTeam, LolMatchTeam::isFirstTower),
                first(withTeam, LolMatchTeam::isFirstDragon),
                first(withTeam, LolMatchTeam::isFirstBaron));
    }

    private static FirstStats first(List<Game> games, Predicate<LolMatchTeam> gotItFirst) {
        List<Game> list = games.stream().filter(g -> gotItFirst.test(g.myTeam())).toList();
        return new FirstStats(list.size(), wins(list));
    }

    /** The {@link #TOP_ITEMS} items most often finished with; an item in two slots counts once per game. */
    private static List<ItemStats> items(List<Game> games) {
        Map<Integer, Tally> tallies = new LinkedHashMap<>();
        for (Game g : games) {
            List<Integer> ids = LolDtos.ids(g.line().getItems());
            if (ids == null) {
                continue;
            }
            ids.stream().limit(ITEM_SLOTS).filter(id -> id != 0).distinct()
                    .forEach(id -> tallies.computeIfAbsent(id, k -> new Tally()).add(g.win()));
        }
        return tallies.entrySet().stream()
                .map(e -> new ItemStats(e.getKey(), e.getValue().games, e.getValue().wins))
                .sorted(Comparator.comparingInt(ItemStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(ItemStats::wins).reversed()))
                .limit(TOP_ITEMS)
                .toList();
    }

    private static List<KeystoneStats> keystones(List<Game> whole) {
        List<Game> withKeystone = whole.stream().filter(g -> g.me().getKeystone() != null).toList();
        return groupBy(withKeystone, g -> List.of(g.me().getKeystone(), Objects.requireNonNullElse(g.me().getPrimaryStyle(), 0)))
                .values().stream()
                .map(list -> new KeystoneStats(list.getFirst().me().getKeystone(), list.getFirst().me().getPrimaryStyle(),
                        list.size(), wins(list)))
                .sorted(Comparator.comparingInt(KeystoneStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(KeystoneStats::wins).reversed()))
                .toList();
    }

    private static List<SpellStats> spells(List<Game> games) {
        List<Game> withSpells = games.stream().filter(g -> g.line().getSummonerSpells() != null).toList();
        return groupBy(withSpells, g -> g.line().getSummonerSpells()).entrySet().stream()
                .map(e -> new SpellStats(e.getKey(), e.getValue().size(), wins(e.getValue())))
                .sorted(Comparator.comparingInt(SpellStats::games).reversed()
                        .thenComparing(Comparator.comparingInt(SpellStats::wins).reversed()))
                .toList();
    }

    /** Newest patch first, compared as numbers ("14.9" is older than "14.10"). */
    private static List<PatchStats> patches(List<Game> whole) {
        List<Game> withPatch = whole.stream().filter(g -> g.match().getPatch() != null).toList();
        return groupBy(withPatch, g -> g.match().getPatch()).entrySet().stream()
                .map(e -> new PatchStats(e.getKey(), e.getValue().size(), wins(e.getValue())))
                .sorted(Comparator.comparing(PatchStats::patch, LolDeepStatsCalculator::comparePatches).reversed())
                .toList();
    }

    static int comparePatches(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            int c = Integer.compare(parseOrZero(x[i]), parseOrZero(y[i]));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(x.length, y.length);
    }

    private static int parseOrZero(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Oldest first, as the charts draw left to right. */
    private static List<TrendPoint> trend(List<Game> newestFirst) {
        return newestFirst.reversed().stream()
                .map(g -> {
                    LolMatchPlayer m = g.line();
                    List<Game> one = List.of(g);
                    return new TrendPoint(m.getMatchId(), m.getEndedAt(), m.isWin(),
                            LolStats.kda(m.getKills(), m.getDeaths(), m.getAssists()),
                            perMinute(one, x -> m.getCs()), perMinute(one, x -> m.getDamage()),
                            perMinute(one, x -> m.getGoldEarned()), perMinute(one, x -> m.getVisionScore()),
                            m.getKillParticipation());
                })
                .toList();
    }

    // ---- helpers ----

    private static BigDecimal kda(List<Game> games) {
        return LolStats.kda(sum(games, g -> g.line().getKills()), sum(games, g -> g.line().getDeaths()),
                sum(games, g -> g.line().getAssists()));
    }

    /**
     * Total over total minutes, counting only the games that have the value, so a long game weighs more than a
     * short one, as in LolStats. Null when no game has it.
     */
    private static BigDecimal perMinute(List<Game> games, Function<Game, Integer> value) {
        long total = 0;
        long seconds = 0;
        for (Game g : games) {
            Integer v = value.apply(g);
            if (v != null) {
                total += v;
                seconds += g.line().getDurationS();
            }
        }
        return seconds == 0 ? null : divide(total * 60, seconds, SCALE);
    }

    /** Mean over the games that have the value; null when none do. */
    private static BigDecimal average(List<Game> games, Function<Game, Integer> value) {
        long total = 0;
        int count = 0;
        for (Game g : games) {
            Integer v = value.apply(g);
            if (v != null) {
                total += v;
                count++;
            }
        }
        return count == 0 ? null : divide(total, count, SCALE);
    }

    /** Sum of one value over the sum of another, as a 0..1 fraction; null when the second sums to zero. */
    private static BigDecimal share(List<Game> games, ToIntFunction<Game> part, ToIntFunction<Game> whole) {
        long total = sum(games, whole);
        return total == 0 ? null : fraction(sum(games, part), total);
    }

    /** Champion damage per gold, over the games that have gold. */
    private static BigDecimal damagePerGold(List<Game> games) {
        List<Game> withGold = games.stream().filter(g -> g.line().getGoldEarned() != null).toList();
        long gold = sum(withGold, g -> g.line().getGoldEarned());
        return gold == 0 ? null : divide(sum(withGold, g -> g.line().getDamage()), gold, SCALE);
    }

    private static BigDecimal meanFraction(List<Game> games, Function<Game, BigDecimal> value) {
        List<BigDecimal> values = games.stream().map(value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), FRACTION_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal fraction(long part, long total) {
        return divide(part, total, FRACTION_SCALE);
    }

    private static BigDecimal divide(long numerator, long denominator, int scale) {
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private static int wins(List<Game> games) {
        return count(games, Game::win);
    }

    private static int count(List<Game> games, Predicate<Game> test) {
        return (int) games.stream().filter(test).count();
    }

    private static int sum(List<Game> games, ToIntFunction<Game> value) {
        return games.stream().mapToInt(value).sum();
    }

    private static int sumOf(List<LolMatchParticipant> lines, ToIntFunction<LolMatchParticipant> value) {
        return lines.stream().mapToInt(value).sum();
    }

    /** Keeps first-seen order, so equally ranked groups stay newest first after the stable sort. */
    private static <T, K> Map<K, List<T>> groupBy(List<T> items, Function<T, K> key) {
        Map<K, List<T>> groups = new LinkedHashMap<>();
        for (T item : items) {
            groups.computeIfAbsent(key.apply(item), k -> new ArrayList<>()).add(item);
        }
        return groups;
    }

    private static Tally[] tallies(int size) {
        Tally[] tallies = new Tally[size];
        for (int i = 0; i < size; i++) {
            tallies[i] = new Tally();
        }
        return tallies;
    }

    /** Games and wins, counted up one game at a time. */
    private static final class Tally {
        int games;
        int wins;

        void add(boolean win) {
            games++;
            if (win) {
                wins++;
            }
        }
    }
}
