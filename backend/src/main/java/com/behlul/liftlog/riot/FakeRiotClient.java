package com.behlul.liftlog.riot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development stand-in for Riot, switched on with RIOT_FAKE=true. Every Riot ID "exists" and gets the same
 * made-up rank and matches each time (seeded from its name), so screens can be built and tested before a
 * real API key is approved. Never used in production: the config refuses to start with both a key and this.
 *
 * <p>Matches are whole: ten players, both teams with objectives and bans, and a timeline. Two of every three
 * matches put the same teammate (the player's "duo") next to them, so the duo pages have something to show.
 */
public class FakeRiotClient implements RiotClient {

    /** A Riot ID with this name is reported as not found; a match id containing it has no timeline. */
    public static final String MISSING_NAME = "notfound";
    /** A Riot ID containing this is always in a live game; one containing {@link #OFFLINE_NAME} never is. */
    public static final String LIVE_NAME = "ingame";
    public static final String OFFLINE_NAME = "offline";
    private static final long LIVE_WINDOW_MS = 20 * 60_000L;
    /** Data Dragon champion keys of {@link #OTHER_CHAMPIONS}, for mastery and live games (which use keys, not names). */
    private static final Map<String, Integer> CHAMPION_KEYS = Map.ofEntries(Map.entry("Ahri", 103), Map.entry("Jinx", 222),
            Map.entry("LeeSin", 64), Map.entry("Thresh", 412), Map.entry("Yasuo", 157), Map.entry("Lux", 99),
            Map.entry("Darius", 122), Map.entry("Ezreal", 81), Map.entry("Kaisa", 145), Map.entry("Garen", 86),
            Map.entry("Viego", 234), Map.entry("Leona", 89), Map.entry("Teemo", 17), Map.entry("Zed", 238),
            Map.entry("Morgana", 25), Map.entry("Vayne", 67), Map.entry("Caitlyn", 51), Map.entry("Malphite", 54),
            Map.entry("Annie", 1), Map.entry("Sona", 37), Map.entry("Graves", 104), Map.entry("Amumu", 32),
            Map.entry("Katarina", 55), Map.entry("Nautilus", 111));
    /** Further champions only mastery lists show: Aatrox, Akali, Alistar, ... */
    private static final int[] MORE_CHAMPION_KEYS = {266, 84, 12, 53, 63, 164, 119, 114, 39, 24};

    private static final String[] CHAMPIONS = {"Ahri", "Jinx", "LeeSin", "Thresh", "Yasuo", "Lux", "Darius", "Ezreal",
            "Kaisa", "Garen", "Viego", "Leona"};
    /** The other nine players pick from these, never the followed player's champion. */
    private static final String[] OTHER_CHAMPIONS = {"Ahri", "Jinx", "LeeSin", "Thresh", "Yasuo", "Lux", "Darius",
            "Ezreal", "Kaisa", "Garen", "Viego", "Leona", "Teemo", "Zed", "Morgana", "Vayne", "Caitlyn", "Malphite",
            "Annie", "Sona", "Graves", "Amumu", "Katarina", "Nautilus"};
    /** Champion keys of champions nobody above plays (Aatrox, Akali, Alistar, ...), so a ban is never also a pick. */
    private static final int[] BAN_KEYS = {266, 84, 12, 53, 63, 164, 119, 114, 39, 24, 141, 11, 555, 92, 235, 517, 777, 221};
    private static final String[] POSITIONS = {"TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"};
    private static final String[] TIERS = {"IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND"};
    private static final String[] DIVISIONS = {"IV", "III", "II", "I"};
    private static final String[] NAMES = {"Kuzey", "Ege", "Deniz", "Mira", "Atlas", "Nova", "Bora", "Lina", "Kaan",
            "Selin", "Arda", "Yaz"};
    /** Real item ids (boots, Infinity Edge, Black Cleaver, ...), so the web app's item icons load. */
    private static final int[] ITEMS = {3006, 3031, 3071, 6672, 3153, 3036, 3047, 3157, 3089};
    /** Flash (4) always; Ignite, Teleport, Heal or Exhaust as the second spell (Smite for junglers). */
    private static final int[] SECOND_SPELLS = {14, 12, 7, 3};
    /** {keystone, its rune tree}: Conqueror, Lethal Tempo, Electrocute, Dark Harvest, Aery, Comet, Grasp, Aftershock. */
    private static final int[][] KEYSTONES = {{8010, 8000}, {8008, 8000}, {8112, 8100}, {8128, 8100}, {8214, 8200},
            {8229, 8200}, {8437, 8400}, {8439, 8400}};
    private static final int[] STYLES = {8000, 8100, 8200, 8300, 8400};

    /** Which player each generated match id belongs to, so match() can put them in it. */
    private final Map<String, String> matchOwners = new ConcurrentHashMap<>();
    /** Riot IDs looked up so far, so the scoreboard can show the followed player's name. */
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findAccount(LolPlatform platform, String gameName, String tagLine) {
        if (gameName.equalsIgnoreCase(MISSING_NAME)) {
            return Optional.empty();
        }
        String puuid = "fake-" + UUID.nameUUIDFromBytes((gameName.toLowerCase() + "#" + tagLine.toLowerCase())
                .getBytes(StandardCharsets.UTF_8));
        Account account = new Account(puuid, gameName, tagLine);
        accounts.put(puuid, account);
        return Optional.of(account);
    }

    @Override
    public Optional<Summoner> summoner(LolPlatform platform, String puuid) {
        Random random = seeded(puuid);
        return Optional.of(new Summoner(random.nextInt(30), 30 + random.nextInt(470)));
    }

    @Override
    public List<LeagueEntry> leagueEntries(LolPlatform platform, String puuid) {
        Random random = seeded(puuid + "league");
        if (random.nextInt(6) == 0) {
            return List.of(); // some players are unranked
        }
        String tier = TIERS[random.nextInt(TIERS.length)];
        String division = DIVISIONS[random.nextInt(DIVISIONS.length)];
        random.nextInt(100); // the old fixed LP, still drawn so wins and losses stay what they were
        // LP moves once an hour, so the rank history chart gets points while developing locally
        int lp = seeded(puuid + "lp" + System.currentTimeMillis() / 3_600_000L).nextInt(100);
        LeagueEntry solo = new LeagueEntry("RANKED_SOLO_5x5", tier, division, lp, 20 + random.nextInt(120),
                20 + random.nextInt(120));
        // Flex from its own seed, so the solo numbers above stay what they were; two of three ranked players play it
        Random flexRandom = seeded(puuid + "flex");
        if (flexRandom.nextInt(3) == 0) {
            return List.of(solo);
        }
        // Flex sits a division or two around solo, as it does for most players
        int flexTier = Math.max(0, Math.min(TIERS.length - 1, indexOf(TIERS, tier) + flexRandom.nextInt(3) - 1));
        int flexLp = seeded(puuid + "flexlp" + System.currentTimeMillis() / 3_600_000L).nextInt(100);
        return List.of(solo, new LeagueEntry("RANKED_FLEX_SR", TIERS[flexTier], DIVISIONS[flexRandom.nextInt(DIVISIONS.length)],
                flexLp, 5 + flexRandom.nextInt(60), 5 + flexRandom.nextInt(60)));
    }

    /**
     * Thirty champions with points falling off from a main of up to a million, levels following the points like
     * Riot's uncapped levels (roughly one per 12 000 points early on, slower later), last played over the past months.
     */
    @Override
    public List<ChampionMastery> championMasteries(LolPlatform platform, String puuid) {
        Random random = seeded(puuid + "mastery");
        List<Integer> keys = sortedChampionKeys();
        for (int key : MORE_CHAMPION_KEYS) {
            keys.add(key);
        }
        Collections.shuffle(keys, random);
        long points = 150_000 + random.nextInt(850_000);
        long now = System.currentTimeMillis();
        List<ChampionMastery> masteries = new ArrayList<>();
        for (int i = 0; i < Math.min(30, keys.size()); i++) {
            int level = (int) Math.max(1, Math.min(60, Math.round(Math.sqrt(points / 1_800.0))));
            long lastPlayed = now - (long) i * (6 + random.nextInt(48)) * 3_600_000L - random.nextInt(3_600_000);
            masteries.add(new ChampionMastery(keys.get(i), level, points, lastPlayed));
            points = Math.max(600, (long) (points * (0.55 + random.nextDouble() * 0.35)));
        }
        return masteries;
    }

    /**
     * About one player in three is in a game at any time, changing every 20 minutes. A Riot ID containing
     * {@link #LIVE_NAME} is always in one and one containing {@link #OFFLINE_NAME} never is, for tests. The game has
     * the player, their duo on the same team and eight made-up players, with start time stable within the window.
     */
    @Override
    public Optional<LiveGame> activeGame(LolPlatform platform, String puuid) {
        long window = System.currentTimeMillis() / LIVE_WINDOW_MS;
        Account account = accounts.get(puuid);
        // ROOT: in a Turkish locale "I" lowers to a dotless "ı" and "Ingame" would not match
        String name = account == null ? "" : account.gameName().toLowerCase(java.util.Locale.ROOT);
        boolean inGame = name.contains(LIVE_NAME) || (!name.contains(OFFLINE_NAME)
                && seeded(puuid + "live" + window).nextInt(3) == 0);
        if (!inGame) {
            return Optional.empty();
        }
        Random random = seeded(puuid + "game" + window);
        long startedAt = window * LIVE_WINDOW_MS + 60_000L + random.nextInt(120_000);
        long length = Math.max(0, (System.currentTimeMillis() - startedAt) / 1000);
        int ownerTeam = random.nextBoolean() ? 100 : 200;
        List<Integer> keys = sortedChampionKeys();
        Collections.shuffle(keys, random);
        List<LiveParticipant> participants = new ArrayList<>();
        for (int slot = 0; slot < 10; slot++) {
            int teamId = slot < 5 ? 100 : 200;
            String participantPuuid;
            String riotId;
            if (teamId == ownerTeam && slot % 5 == 0) {
                participantPuuid = puuid;
                riotId = account == null ? "Player#TR1" : account.gameName() + "#" + account.tagLine();
            } else if (teamId == ownerTeam && slot % 5 == 1) {
                participantPuuid = duoOf(puuid);
                riotId = "Duo" + participantPuuid.substring(participantPuuid.length() - 4) + "#TR1";
            } else {
                participantPuuid = "fake-live-" + Integer.toHexString((puuid + window).hashCode()) + "-" + slot;
                riotId = NAMES[random.nextInt(NAMES.length)] + slot + "#TR1";
            }
            int[] keystone = KEYSTONES[random.nextInt(KEYSTONES.length)];
            int sub;
            do {
                sub = STYLES[random.nextInt(STYLES.length)];
            } while (sub == keystone[1]);
            participants.add(new LiveParticipant(participantPuuid, riotId, teamId, keys.get(slot), 4,
                    SECOND_SPELLS[random.nextInt(SECOND_SPELLS.length)], false,
                    new LivePerks(List.of(keystone[0], 8000 + random.nextInt(400)), keystone[1], sub)));
        }
        List<LiveBan> bans = new ArrayList<>();
        for (int turn = 0; turn < 10; turn++) {
            bans.add(new LiveBan(BAN_KEYS[(turn + (int) (window % 7)) % BAN_KEYS.length], turn < 5 ? 100 : 200, turn + 1));
        }
        return Optional.of(new LiveGame(Math.abs((puuid + window).hashCode()), "CLASSIC", random.nextInt(4) == 0 ? 440 : 420,
                startedAt, length, participants, bans));
    }

    @Override
    public List<String> recentMatchIds(LolPlatform platform, String puuid, int count) {
        String prefix = platform.name() + "_" + Integer.toHexString(puuid.hashCode()) + "_";
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = prefix + (500 - i);
            matchOwners.put(id, puuid);
            ids.add(id);
        }
        return ids;
    }

    @Override
    public Optional<Match> match(LolPlatform platform, String matchId) {
        String owner = matchOwners.get(matchId);
        Account account = owner == null ? null : accounts.get(owner);
        return Optional.of(generate(matchId, owner, account, null));
    }

    /**
     * A generated match with {@code teammatePuuid} on the owner's team, for tests that need two known players in
     * one game (two group members playing together). The match id must end in "_" and a number, like real ones.
     */
    public static Match matchWith(String matchId, String ownerPuuid, String teammatePuuid) {
        return generate(matchId, ownerPuuid, null, teammatePuuid);
    }

    /**
     * Gold, xp and cs grow towards each player's end-of-game numbers, one frame a minute plus a last one at the
     * end, like Riot's. Participant ids are those of {@link #match}.
     */
    @Override
    public Optional<Timeline> timeline(LolPlatform platform, String matchId) {
        if (matchId.contains(MISSING_NAME)) {
            return Optional.empty();
        }
        Match match = generate(matchId, matchOwners.get(matchId), null, null);
        Random random = seeded(matchId + "timeline");
        long durationMs = match.gameDuration() * 1000;
        List<Long> timestamps = new ArrayList<>();
        for (long t = 0; t < durationMs; t += 60_000) {
            // Riot's frames land a little after each full minute
            timestamps.add(t == 0 ? 0 : t + random.nextInt(800));
        }
        timestamps.add(durationMs);

        List<Frame> frames = new ArrayList<>();
        for (long timestamp : timestamps) {
            double progress = (double) timestamp / durationMs;
            Map<String, ParticipantFrame> participantFrames = new LinkedHashMap<>();
            for (Participant p : match.participants()) {
                // Leads swing a little from minute to minute instead of growing in a straight line
                double wobble = timestamp == 0 || timestamp == durationMs ? 1 : 0.92 + random.nextDouble() * 0.16;
                int gold = 500 + (int) ((p.goldEarned() - 500) * Math.pow(progress, 1.1) * wobble);
                int level = Math.max(1, Math.min(18, 1 + (int) Math.round((p.champLevel() - 1) * Math.sqrt(progress))));
                boolean jungle = "JUNGLE".equals(p.teamPosition());
                participantFrames.put(String.valueOf(p.participantId()), new ParticipantFrame(gold,
                        (int) (p.champLevel() * 1_000 * progress * wobble),
                        (int) (p.totalMinionsKilled() * progress), jungle ? (int) (p.neutralMinionsKilled() * progress) : 0,
                        level));
            }
            frames.add(new Frame(timestamp, participantFrames));
        }
        return Optional.of(new Timeline(matchId, frames));
    }

    /** The duo partner each player keeps queueing with: same puuid every time, derived from theirs. */
    public static String duoOf(String ownerPuuid) {
        return "fake-duo-" + Integer.toHexString(String.valueOf(ownerPuuid).hashCode());
    }

    /**
     * Draws the followed player's line exactly as before full matches existed, then the rest of the match after
     * it, so the followed player's numbers for a match id stay what they were (the random sequence is only
     * extended).
     */
    private static Match generate(String matchId, String ownerPuuid, Account ownerAccount, String forcedTeammate) {
        Random random = seeded(matchId);
        int number = Integer.parseInt(matchId.substring(matchId.lastIndexOf('_') + 1));
        boolean win = random.nextInt(100) < 53;
        Line owner = new Line();
        owner.puuid = ownerPuuid;
        owner.champion = CHAMPIONS[random.nextInt(CHAMPIONS.length)];
        owner.position = POSITIONS[random.nextInt(POSITIONS.length)];
        owner.kills = random.nextInt(win ? 14 : 8);
        owner.deaths = random.nextInt(10);
        owner.assists = random.nextInt(16);
        owner.minions = 90 + random.nextInt(180);
        owner.neutral = random.nextInt(40);
        owner.damage = 8_000 + random.nextInt(30_000);
        owner.win = win;
        drawDetails(owner, random);
        int queueId = random.nextInt(4) == 0 ? 440 : 420;
        int duration = 1_300 + random.nextInt(1_100);
        if (ownerAccount != null) {
            owner.name = ownerAccount.gameName();
            owner.tag = ownerAccount.tagLine();
        }

        // Everything below is new with full matches
        int ownerTeam = random.nextBoolean() ? 100 : 200;
        int ownerSlot = indexOf(POSITIONS, owner.position);
        String duo = forcedTeammate != null ? forcedTeammate : number % 3 != 0 ? duoOf(ownerPuuid) : null;
        List<String> champions = new ArrayList<>(List.of(OTHER_CHAMPIONS));
        champions.remove(owner.champion);
        Collections.shuffle(champions, random);

        List<Line> lines = new ArrayList<>();
        int participantId = 1;
        for (int teamId : new int[]{100, 200}) {
            boolean teamWon = (teamId == ownerTeam) == win;
            for (int slot = 0; slot < POSITIONS.length; slot++) {
                Line line;
                if (teamId == ownerTeam && slot == ownerSlot) {
                    line = owner;
                } else {
                    line = randomLine(random, teamWon, POSITIONS[slot], champions.removeFirst());
                    if (teamId == ownerTeam && duo != null && slot == (ownerSlot + 1) % POSITIONS.length) {
                        line.puuid = duo;
                        line.name = forcedTeammate != null ? "Mate" + slot : "Duo" + duo.substring(duo.length() - 4);
                        line.tag = "TR1";
                    } else {
                        line.puuid = "fake-" + matchId + "-" + participantId;
                        line.name = NAMES[random.nextInt(NAMES.length)] + participantId;
                        line.tag = "TR1";
                    }
                }
                line.participantId = participantId++;
                line.teamId = teamId;
                drawScoreboard(line, random, duration);
                lines.add(line);
            }
        }
        shareTeamNumbers(lines, owner);
        List<Team> teams = teams(random, lines, win == (ownerTeam == 100));

        // Newest match ended about an hour ago, the rest four hours apart, so lists read like real recent play
        long hour = 3_600_000L;
        long endedAt = (System.currentTimeMillis() / hour) * hour - (500 - number) * 4 * hour - hour;
        // Older matches were played on older patches, about one per two weeks of play
        String version = "14." + Math.max(1, 22 - (500 - number) / 25) + ".612.3456";
        return new Match(matchId, queueId, endedAt, duration, "CLASSIC",
                lines.stream().map(Line::toParticipant).toList(), version, teams);
    }

    /** One of the other nine players, with numbers in the same ranges as the followed player's. */
    private static Line randomLine(Random random, boolean win, String position, String champion) {
        Line line = new Line();
        line.champion = champion;
        line.position = position;
        line.win = win;
        line.kills = random.nextInt(win ? 14 : 8);
        line.deaths = random.nextInt(10);
        line.assists = random.nextInt(16);
        boolean jungle = "JUNGLE".equals(position);
        boolean support = "UTILITY".equals(position);
        line.minions = support ? 10 + random.nextInt(40) : jungle ? 20 + random.nextInt(40) : 120 + random.nextInt(150);
        line.neutral = jungle ? 100 + random.nextInt(80) : random.nextInt(20);
        line.damage = 6_000 + random.nextInt(30_000);
        drawDetails(line, random);
        return line;
    }

    /**
     * The detail numbers, drawn after the basic ones (see {@link #generate}). Kill participation and damage share
     * drawn here are kept for the followed player and recomputed from the scoreboard for the others.
     */
    private static void drawDetails(Line p, Random random) {
        for (int slot = 0; slot < 6; slot++) {
            // Now and then an empty slot, as in a short game
            p.items[slot] = random.nextInt(6) == 0 ? 0 : ITEMS[random.nextInt(ITEMS.length)];
        }
        p.items[6] = 3363; // the trinket slot
        boolean jungle = "JUNGLE".equals(p.position);
        p.killParticipation = 0.3 + random.nextDouble() * 0.5;
        p.damageShare = 0.1 + random.nextDouble() * 0.3;
        p.gold = 7_000 + random.nextInt(9_000);
        p.vision = 10 + random.nextInt(50);
        p.level = 11 + random.nextInt(8);
        p.damageTaken = 10_000 + random.nextInt(30_000);
        p.largestMultiKill = 1 + weightedMultiKill(random);
        p.wardsPlaced = 4 + random.nextInt(20);
        p.spell2 = jungle ? 11 : SECOND_SPELLS[random.nextInt(SECOND_SPELLS.length)];
    }

    /** The scoreboard-only numbers every player gets. */
    private static void drawScoreboard(Line p, Random random, int duration) {
        p.objectiveDamage = 1_000 + random.nextInt("JUNGLE".equals(p.position) ? 40_000 : 12_000);
        p.buildingDamage = random.nextInt(9_000);
        p.heal = 500 + random.nextInt(12_000);
        p.shielding = "UTILITY".equals(p.position) ? 1_000 + random.nextInt(9_000) : random.nextInt(500);
        p.ccTime = random.nextInt(60);
        p.timeDead = Math.min(duration / 3, p.deaths * (15 + random.nextInt(30)));
        p.wardsKilled = random.nextInt(10);
        p.controlWards = random.nextInt(6);
        // Multi kills follow the largest one: a triple also means at least one double
        p.doubles = p.largestMultiKill >= 2 ? 1 + random.nextInt(2) : 0;
        p.triples = p.largestMultiKill >= 3 ? 1 : 0;
        p.quadras = p.largestMultiKill >= 4 ? 1 : 0;
        p.pentas = p.largestMultiKill >= 5 ? 1 : 0;
        p.soloKills = random.nextInt(Math.max(1, p.kills / 2 + 1));
        int[] keystone = KEYSTONES[random.nextInt(KEYSTONES.length)];
        p.keystone = keystone[0];
        p.primaryStyle = keystone[1];
        do {
            p.secondaryStyle = STYLES[random.nextInt(STYLES.length)];
        } while (p.secondaryStyle == p.primaryStyle);
        if ("UTILITY".equals(p.position)) {
            p.items[6] = 3364; // supports swap to the sweeper
        }
    }

    /** Kill participation and damage share from the team totals, and first blood for one player who has a kill. */
    private static void shareTeamNumbers(List<Line> lines, Line owner) {
        for (int teamId : new int[]{100, 200}) {
            List<Line> team = lines.stream().filter(l -> l.teamId == teamId).toList();
            int kills = team.stream().mapToInt(l -> l.kills).sum();
            int damage = team.stream().mapToInt(l -> l.damage).sum();
            for (Line l : team) {
                if (l != owner) {
                    l.killParticipation = kills == 0 ? 0 : Math.min(1, (double) (l.kills + l.assists) / kills);
                    l.damageShare = damage == 0 ? 0 : (double) l.damage / damage;
                }
            }
        }
        lines.stream().filter(l -> l.kills > 0).findFirst().ifPresent(l -> l.firstBloodKill = true);
    }

    private static List<Team> teams(Random random, List<Line> lines, boolean blueWon) {
        boolean firstBloodBlue = lines.stream().filter(l -> l.firstBloodKill).anyMatch(l -> l.teamId == 100);
        List<Integer> bans = new ArrayList<>();
        for (int key : BAN_KEYS) {
            bans.add(key);
        }
        Collections.shuffle(bans, random);
        int grubs = random.nextInt(7);
        List<Team> teams = new ArrayList<>();
        int firstTowerTeam = random.nextInt(3) == 0 ? (blueWon ? 200 : 100) : (blueWon ? 100 : 200);
        Integer firstDragonTeam = null;
        Integer firstBaronTeam = null;
        int[][] numbers = new int[2][];
        for (int side = 0; side < 2; side++) {
            boolean won = (side == 0) == blueWon;
            // towers, inhibitors, dragons, barons, heralds, atakhan
            numbers[side] = new int[]{won ? 6 + random.nextInt(6) : random.nextInt(6), won ? 1 + random.nextInt(3) : random.nextInt(2),
                    won ? 2 + random.nextInt(3) : random.nextInt(3), won ? random.nextInt(3) : random.nextInt(2),
                    random.nextInt(2), won && random.nextBoolean() ? 1 : 0};
        }
        if (numbers[0][2] + numbers[1][2] > 0) {
            firstDragonTeam = numbers[1][2] == 0 || (numbers[0][2] > 0 && random.nextBoolean()) ? 100 : 200;
        }
        if (numbers[0][3] + numbers[1][3] > 0) {
            firstBaronTeam = numbers[1][3] == 0 || (numbers[0][3] > 0 && random.nextBoolean()) ? 100 : 200;
        }
        for (int side = 0; side < 2; side++) {
            int teamId = side == 0 ? 100 : 200;
            int[] n = numbers[side];
            int kills = lines.stream().filter(l -> l.teamId == teamId).mapToInt(l -> l.kills).sum();
            // Now and then a skipped ban (-1), as Riot sends it
            List<Ban> teamBans = bans.subList(side * 5, side * 5 + 5).stream()
                    .map(key -> new Ban(random.nextInt(15) == 0 ? -1 : key)).toList();
            Objectives objectives = new Objectives(
                    new Objective(Integer.valueOf(teamId).equals(firstBaronTeam), n[3]),
                    new Objective((teamId == 100) == firstBloodBlue, kills),
                    new Objective(Integer.valueOf(teamId).equals(firstDragonTeam), n[2]),
                    new Objective(false, side == 0 ? grubs : 6 - grubs),
                    new Objective(false, n[1]),
                    new Objective(false, n[4]),
                    new Objective(teamId == firstTowerTeam, n[0]),
                    new Objective(false, n[5]));
            teams.add(new Team(teamId, (side == 0) == blueWon, teamBans, objectives));
        }
        return teams;
    }

    /** Extra kills beyond one: mostly none, a penta now and then. */
    private static int weightedMultiKill(Random random) {
        int roll = random.nextInt(100);
        return roll < 45 ? 0 : roll < 80 ? 1 : roll < 94 ? 2 : roll < 99 ? 3 : 4;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        throw new IllegalArgumentException(value);
    }

    /** Sorted: Map.of iterates in an order that changes between runs, and the fake has to give the same data each time. */
    private static List<Integer> sortedChampionKeys() {
        return new ArrayList<>(CHAMPION_KEYS.values().stream().sorted().toList());
    }

    private static Random seeded(String text) {
        return new Random(text.hashCode());
    }

    /** A player's line while it is being generated; Participant is an immutable record with thirty-odd fields. */
    private static final class Line {
        String puuid;
        String name;
        String tag;
        int participantId;
        int teamId;
        String champion;
        String position;
        int kills;
        int deaths;
        int assists;
        int minions;
        int neutral;
        int damage;
        boolean win;
        final int[] items = new int[7];
        double killParticipation;
        double damageShare;
        int gold;
        int vision;
        int level;
        int damageTaken;
        int largestMultiKill;
        int wardsPlaced;
        int spell2;
        int objectiveDamage;
        int buildingDamage;
        int heal;
        int shielding;
        int ccTime;
        int timeDead;
        int wardsKilled;
        int controlWards;
        int doubles;
        int triples;
        int quadras;
        int pentas;
        int soloKills;
        boolean firstBloodKill;
        int keystone;
        int primaryStyle;
        int secondaryStyle;

        Participant toParticipant() {
            Perks perks = new Perks(List.of(
                    new PerkStyle(primaryStyle, List.of(new PerkSelection(keystone))),
                    new PerkStyle(secondaryStyle, List.of())));
            return new Participant(puuid, champion, position, kills, deaths, assists, minions, neutral, damage, win,
                    gold, vision, level, damageTaken, largestMultiKill, wardsPlaced, items[0], items[1], items[2],
                    items[3], items[4], items[5], items[6], 4, spell2,
                    new Challenges(killParticipation, damageShare, soloKills),
                    participantId, name, tag, teamId, objectiveDamage, buildingDamage, heal, shielding, ccTime, timeDead,
                    wardsKilled, controlWards, doubles, triples, quadras, pentas, firstBloodKill, false, perks);
        }
    }
}
