package com.behlul.liftlog.riot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 */
public class FakeRiotClient implements RiotClient {

    /** A Riot ID with this name is reported as not found, to exercise the error path. */
    public static final String MISSING_NAME = "notfound";

    private static final String[] CHAMPIONS = {"Ahri", "Jinx", "LeeSin", "Thresh", "Yasuo", "Lux", "Darius", "Ezreal",
            "Kaisa", "Garen", "Viego", "Leona"};
    private static final String[] POSITIONS = {"TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"};
    private static final String[] TIERS = {"IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND"};
    private static final String[] DIVISIONS = {"IV", "III", "II", "I"};
    /** Real item ids (boots, Infinity Edge, Black Cleaver, ...), so the web app's item icons load. */
    private static final int[] ITEMS = {3006, 3031, 3071, 6672, 3153, 3036, 3047, 3157, 3089};
    /** Flash (4) always; Ignite, Teleport, Heal or Exhaust as the second spell (Smite for junglers). */
    private static final int[] SECOND_SPELLS = {14, 12, 7, 3};

    /** Which player each generated match id belongs to, so match() can put them in it. */
    private final Map<String, String> matchOwners = new ConcurrentHashMap<>();

    @Override
    public Optional<Account> findAccount(LolPlatform platform, String gameName, String tagLine) {
        if (gameName.equalsIgnoreCase(MISSING_NAME)) {
            return Optional.empty();
        }
        String puuid = "fake-" + UUID.nameUUIDFromBytes((gameName.toLowerCase() + "#" + tagLine.toLowerCase())
                .getBytes(StandardCharsets.UTF_8));
        return Optional.of(new Account(puuid, gameName, tagLine));
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
        return List.of(new LeagueEntry("RANKED_SOLO_5x5", tier, division, lp, 20 + random.nextInt(120),
                20 + random.nextInt(120)));
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
        Random random = seeded(matchId);
        int number = Integer.parseInt(matchId.substring(matchId.lastIndexOf('_') + 1));
        boolean win = random.nextInt(100) < 53;
        Participant owner = new Participant(matchOwners.get(matchId), CHAMPIONS[random.nextInt(CHAMPIONS.length)],
                POSITIONS[random.nextInt(POSITIONS.length)], random.nextInt(win ? 14 : 8), random.nextInt(10),
                random.nextInt(16), 90 + random.nextInt(180), random.nextInt(40), 8_000 + random.nextInt(30_000), win,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null);
        owner = withDetails(owner, random);
        // Newest match ended about an hour ago, the rest four hours apart, so lists read like real recent play
        long hour = 3_600_000L;
        long endedAt = (System.currentTimeMillis() / hour) * hour - (500 - number) * 4 * hour - hour;
        return Optional.of(new Match(matchId, random.nextInt(4) == 0 ? 440 : 420, endedAt, 1_300 + random.nextInt(1_100),
                "CLASSIC", List.of(owner)));
    }

    /**
     * Fills the detail numbers after the basic ones, so the basic numbers of a match stay what they were before
     * the details existed (the random sequence is only extended).
     */
    private static Participant withDetails(Participant p, Random random) {
        int[] items = new int[7];
        for (int slot = 0; slot < 6; slot++) {
            // Now and then an empty slot, as in a short game
            items[slot] = random.nextInt(6) == 0 ? 0 : ITEMS[random.nextInt(ITEMS.length)];
        }
        items[6] = 3363; // the trinket slot
        boolean jungle = "JUNGLE".equals(p.teamPosition());
        Challenges challenges = new Challenges(0.3 + random.nextDouble() * 0.5, 0.1 + random.nextDouble() * 0.3);
        return new Participant(p.puuid(), p.championName(), p.teamPosition(), p.kills(), p.deaths(), p.assists(),
                p.totalMinionsKilled(), p.neutralMinionsKilled(), p.totalDamageDealtToChampions(), p.win(),
                7_000 + random.nextInt(9_000), 10 + random.nextInt(50), 11 + random.nextInt(8), 10_000 + random.nextInt(30_000),
                1 + weightedMultiKill(random), 4 + random.nextInt(20), items[0], items[1], items[2], items[3], items[4],
                items[5], items[6], 4, jungle ? 11 : SECOND_SPELLS[random.nextInt(SECOND_SPELLS.length)], challenges);
    }

    /** Extra kills beyond one: mostly none, a penta now and then. */
    private static int weightedMultiKill(Random random) {
        int roll = random.nextInt(100);
        return roll < 45 ? 0 : roll < 80 ? 1 : roll < 94 ? 2 : roll < 99 ? 3 : 4;
    }

    private static Random seeded(String text) {
        return new Random(text.hashCode());
    }
}
