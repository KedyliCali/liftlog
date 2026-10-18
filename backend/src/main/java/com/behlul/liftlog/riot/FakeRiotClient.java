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
    public Summoner summoner(LolPlatform platform, String puuid) {
        Random random = seeded(puuid);
        return new Summoner(random.nextInt(30), 30 + random.nextInt(470));
    }

    @Override
    public List<LeagueEntry> leagueEntries(LolPlatform platform, String puuid) {
        Random random = seeded(puuid + "league");
        if (random.nextInt(6) == 0) {
            return List.of(); // some players are unranked
        }
        return List.of(new LeagueEntry("RANKED_SOLO_5x5", TIERS[random.nextInt(TIERS.length)],
                DIVISIONS[random.nextInt(DIVISIONS.length)], random.nextInt(100), 20 + random.nextInt(120),
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
    public Match match(LolPlatform platform, String matchId) {
        Random random = seeded(matchId);
        int number = Integer.parseInt(matchId.substring(matchId.lastIndexOf('_') + 1));
        boolean win = random.nextInt(100) < 53;
        Participant owner = new Participant(matchOwners.get(matchId), CHAMPIONS[random.nextInt(CHAMPIONS.length)],
                POSITIONS[random.nextInt(POSITIONS.length)], random.nextInt(win ? 14 : 8), random.nextInt(10),
                random.nextInt(16), 90 + random.nextInt(180), random.nextInt(40), 8_000 + random.nextInt(30_000), win);
        // Newest match ended about an hour ago, the rest four hours apart, so lists read like real recent play
        long hour = 3_600_000L;
        long endedAt = (System.currentTimeMillis() / hour) * hour - (500 - number) * 4 * hour - hour;
        return new Match(matchId, random.nextInt(4) == 0 ? 440 : 420, endedAt, 1_300 + random.nextInt(1_100), List.of(owner));
    }

    private static Random seeded(String text) {
        return new Random(text.hashCode());
    }
}
