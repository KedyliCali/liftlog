package com.behlul.liftlog;

import com.behlul.liftlog.riot.FakeRiotClient;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.service.LolMatchStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole matches end to end with the fake Riot client: detailed stats, match pages, timelines, group duos and the
 * backfill of matches stored before whole matches were. Real security, schema and PostgreSQL (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LolDeepStatsIntegrationTest {

    private static final String GROUP = "/api/v1/lol/group";
    private static final String MATCHES = "/api/v1/lol/matches/";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LolMatchStore matchStore;
    @Autowired private PlatformTransactionManager transactionManager;

    private static String stats(String puuid) {
        return "/api/v1/lol/players/" + puuid + "/stats";
    }

    @Test
    void detailedStatsCountWholeMatchesAndRejectOtherFilters() throws Exception {
        Account me = newAccount("Behlul");
        String puuid = follow(me, uniqueRiotId("Deep"));

        // The fake's duo plays in two of every three matches: 7 of the 10 the first refresh stored
        asUser(get(stats(puuid) + "?queue=ALL&games=20&tzOffsetMinutes=180"), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queue").value("ALL"))
                .andExpect(jsonPath("$.games").value(20))
                .andExpect(jsonPath("$.gamesFound").value(10))
                .andExpect(jsonPath("$.gamesWithFullData").value(10))
                .andExpect(jsonPath("$.wins").isNumber())
                .andExpect(jsonPath("$.overview.kda").isNumber())
                .andExpect(jsonPath("$.overview.goldPerMinute").isNumber())
                .andExpect(jsonPath("$.overview.avgWardsKilled").isNumber())
                .andExpect(jsonPath("$.overview.deadShare").isNumber())
                .andExpect(jsonPath("$.overview.firstBloodRate").isNumber())
                .andExpect(jsonPath("$.overview.multiKills.double").isNumber())
                .andExpect(jsonPath("$.overview.multiKills.penta").isNumber())
                .andExpect(jsonPath("$.overview.avgGameMinutes").isNumber())
                .andExpect(jsonPath("$.champions[0].lastPlayed").isNotEmpty())
                .andExpect(jsonPath("$.roles[0].position").isString())
                .andExpect(jsonPath("$.duos.length()").value(1))
                .andExpect(jsonPath("$.duos[0].puuid").value(FakeRiotClient.duoOf(puuid)))
                .andExpect(jsonPath("$.duos[0].games").value(7))
                .andExpect(jsonPath("$.duos[0].riotName").isString())
                .andExpect(jsonPath("$.duos[0].myKda").isNumber())
                .andExpect(jsonPath("$.duos[0].tracked").value(false))
                .andExpect(jsonPath("$.matchups[0].avgGoldDiff").isNumber())
                .andExpect(jsonPath("$.enemyChampions").isArray())
                .andExpect(jsonPath("$.allyChampions").isArray())
                .andExpect(jsonPath("$.sides.length()").value(2))
                .andExpect(jsonPath("$.durations.length()").value(4))
                .andExpect(jsonPath("$.hours[0].label").value("00-06"))
                .andExpect(jsonPath("$.weekdays.length()").value(7))
                .andExpect(jsonPath("$.objectives.avgTeamTowers").isNumber())
                .andExpect(jsonPath("$.objectives.firstTower.games").isNumber())
                .andExpect(jsonPath("$.items[0].itemId").isNumber())
                .andExpect(jsonPath("$.keystones[0].primaryStyle").isNumber())
                .andExpect(jsonPath("$.spells[0].spells").value(matchesPattern("^4,\\d+$")))
                .andExpect(jsonPath("$.patches[0].patch").value(matchesPattern("^14\\.\\d+$")))
                .andExpect(jsonPath("$.trend.length()").value(10));

        // Defaults: every queue, the last 20 games, UTC
        asUser(get(stats(puuid)), me)
                .andExpect(jsonPath("$.queue").value("ALL"))
                .andExpect(jsonPath("$.games").value(20));
        // The fake plays solo and flex only, so the two together are every game, and ARAM is none
        int solo = read(asUser(get(stats(puuid) + "?queue=SOLO&games=100"), me).andReturn()).get("gamesFound").asInt();
        int flex = read(asUser(get(stats(puuid) + "?queue=FLEX&games=50"), me).andReturn()).get("gamesFound").asInt();
        assertThat(solo + flex).isEqualTo(10);
        asUser(get(stats(puuid) + "?queue=ARAM"), me)
                .andExpect(jsonPath("$.gamesFound").value(0))
                .andExpect(jsonPath("$.overview.kda").doesNotExist());

        for (String bad : List.of("queue=RANKED", "games=30", "games=abc", "tzOffsetMinutes=900",
                "tzOffsetMinutes=-721")) {
            asUser(get(stats(puuid) + "?" + bad), me).andExpect(status().isBadRequest());
        }
        asUser(get(stats(puuid) + "?games=30"), me).andExpect(jsonPath("$.fieldErrors.games").isString());

        // Same visibility as the player page
        Account stranger = newAccount(null);
        asUser(get(stats(puuid)), stranger).andExpect(status().isNotFound());
        mockMvc.perform(get(stats(puuid))).andExpect(status().isUnauthorized());

        // The match list on the player page now knows each match's side
        asUser(get("/api/v1/lol/players/" + puuid), me)
                .andExpect(jsonPath("$.matches[0].teamId", anyOf(is(100), is(200))));
    }

    @Test
    void matchPagesShowTheWholeScoreboardToAnySignedInUser() throws Exception {
        Account me = newAccount(null);
        String puuid = follow(me, uniqueRiotId("Page"));
        String matchId = read(asUser(get("/api/v1/lol/players/" + puuid), me).andReturn())
                .get("matches").get(0).get("matchId").asString();

        asUser(get(MATCHES + matchId), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.gameVersion").value(matchesPattern("^14\\..*")))
                .andExpect(jsonPath("$.durationS").isNumber())
                .andExpect(jsonPath("$.teams.length()").value(2))
                .andExpect(jsonPath("$.teams[0].teamId").value(100))
                .andExpect(jsonPath("$.teams[0].bans.length()").value(5))
                .andExpect(jsonPath("$.teams[0].grubs").isNumber())
                .andExpect(jsonPath("$.participants.length()").value(10))
                .andExpect(jsonPath("$.participants[0].participantId").value(1))
                .andExpect(jsonPath("$.participants[0].items.length()").value(7))
                .andExpect(jsonPath("$.participants[0].spells.length()").value(2))
                .andExpect(jsonPath("$.participants[0].keystone").isNumber())
                .andExpect(jsonPath("$.participants[?(@.puuid == '" + puuid + "')].tracked", contains(true)))
                .andExpect(jsonPath("$.timelineAvailable").value(true));

        // Public game data: someone who follows nobody can open it too, but nobody is marked as theirs
        Account stranger = newAccount(null);
        asUser(get(MATCHES + matchId), stranger)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[?(@.puuid == '" + puuid + "')].tracked", contains(false)));
        asUser(get(MATCHES + "TR1_never_1"), me).andExpect(status().isNotFound());
        asUser(get(MATCHES + "TR1_never_1/timeline"), me).andExpect(status().isNotFound());
        mockMvc.perform(get(MATCHES + matchId)).andExpect(status().isUnauthorized());
    }

    @Test
    void theTimelineIsDownloadedOnceThenServedFromTheDatabase() throws Exception {
        Account me = newAccount(null);
        String puuid = follow(me, uniqueRiotId("Time"));
        String matchId = read(asUser(get("/api/v1/lol/players/" + puuid), me).andReturn())
                .get("matches").get(0).get("matchId").asString();

        JsonNode timeline = read(asUser(get(MATCHES + matchId + "/timeline"), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId))
                .andExpect(jsonPath("$.minutes.length()", greaterThan(20)))
                .andExpect(jsonPath("$.minutes[0].blueGold").value(2_500))
                .andExpect(jsonPath("$.participants.length()").value(10))
                .andExpect(jsonPath("$.participants[0].at10.gold").isNumber())
                .andExpect(jsonPath("$.participants[0].at15.cs").isNumber())
                .andReturn());
        assertThat(timeline.get("participants").get(0).get("gold").size()).isEqualTo(timeline.get("minutes").size());
        Integer frames = jdbcTemplate.queryForObject("select count(*) from lol_match_frames where match_id = ?",
                Integer.class, matchId);
        assertThat(frames).isEqualTo(10 * timeline.get("minutes").size());

        asUser(get(MATCHES + matchId + "/timeline"), me).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select count(*) from lol_match_frames where match_id = ?",
                Integer.class, matchId)).isEqualTo(frames);
        assertThat(jdbcTemplate.queryForObject("select timeline_fetched from lol_matches where match_id = ?",
                Boolean.class, matchId)).isTrue();
    }

    @Test
    void aTimelineRiotNoLongerHasIsA404AndTheMatchPageSaysSo() throws Exception {
        Account me = newAccount(null);
        // The fake has no timeline for match ids containing "notfound"
        String matchId = "TR1_" + FakeRiotClient.MISSING_NAME + UUID.randomUUID().toString().substring(0, 6) + "_42";
        storeWhole(FakeRiotClient.matchWith(matchId, "owner-" + UUID.randomUUID(), null));

        asUser(get(MATCHES + matchId), me).andExpect(jsonPath("$.timelineAvailable").value(true));
        asUser(get(MATCHES + matchId + "/timeline"), me).andExpect(status().isNotFound());
        // Remembered: the page stops offering it, and asking again is still a 404
        asUser(get(MATCHES + matchId), me).andExpect(jsonPath("$.timelineAvailable").value(false));
        asUser(get(MATCHES + matchId + "/timeline"), me).andExpect(status().isNotFound());
    }

    @Test
    void groupDuosArePairsOfMembersOnOneTeam() throws Exception {
        Account owner = newAccount("Owner");
        Account friend = newAccount("Friend");
        Account loner = newAccount(null);
        String code = read(authed(post(GROUP), owner, "{\"name\":\"Duo Queue\"}")
                .andExpect(status().isCreated()).andReturn()).get("inviteCode").asString();
        authed(post(GROUP + "/join"), friend, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());

        asUser(get(GROUP + "/duos"), loner).andExpect(status().isNoContent());
        // In a group, but nobody linked an account yet
        asUser(get(GROUP + "/duos"), owner).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String ownerPuuid = linkAccount(owner, uniqueRiotId("Own"));
        String friendPuuid = linkAccount(friend, uniqueRiotId("Fri"));
        // Their own fake matches never share a game, so two shared ones are stored the way a refresh would
        String prefix = "TR1_gd" + UUID.randomUUID().toString().substring(0, 6) + "_";
        RiotClient.Match first = FakeRiotClient.matchWith(prefix + 1, ownerPuuid, friendPuuid);
        RiotClient.Match second = FakeRiotClient.matchWith(prefix + 2, ownerPuuid, friendPuuid);
        storeWhole(first);
        storeWhole(second);
        int wins = (ownerWon(first, ownerPuuid) ? 1 : 0) + (ownerWon(second, ownerPuuid) ? 1 : 0);

        asUser(get(GROUP + "/duos"), friend)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].a.userId").value(owner.id()))
                .andExpect(jsonPath("$[0].a.displayName").value("Owner"))
                .andExpect(jsonPath("$[0].b.userId").value(friend.id()))
                .andExpect(jsonPath("$[0].games").value(2))
                .andExpect(jsonPath("$[0].wins").value(wins));
    }

    @Test
    void storedMatchesWithoutAWholeMatchAreRedownloadedWithTheLeftoverBudget() throws Exception {
        Account me = newAccount(null);
        String puuid = follow(me, uniqueRiotId("Back"));
        // The first refresh stored the newest ten (…_500 to …_491) whole
        List<String> ids = new FakeRiotClient().recentMatchIds(LolPlatform.TR1, puuid, 100);
        assertThat(count("select count(*) from lol_matches where match_id = ?", ids.get(0))).isOne();

        // As if stored before whole matches: one of the ten loses its whole match, and 85 older lines exist
        // with only the basic numbers, leaving five new matches for the next refresh
        jdbcTemplate.update("delete from lol_matches where match_id = ?", ids.get(5));
        LocalDateTime ended = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (String id : ids.subList(10, 95)) {
            jdbcTemplate.update("""
                    insert into lol_match_players (match_id, puuid, queue_id, ended_at, duration_s, champion, position,
                                                   kills, deaths, assists, cs, damage, win)
                    values (?, ?, 420, ?, 1800, 'Ahri', 'MIDDLE', 5, 3, 7, 180, 20000, true)
                    """, id, puuid, ended);
        }
        jdbcTemplate.update("update lol_players set refreshed_at = ? where puuid = ?", LocalDateTime.now().minusDays(1), puuid);

        asUser(get("/api/v1/lol/players/" + puuid), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches.length()").value(100));

        // Five new matches, then the budget's other five go to the newest lines without a whole match
        for (String id : ids.subList(95, 100)) {
            assertThat(count("select count(*) from lol_matches where match_id = ?", id)).as(id).isOne();
        }
        for (String id : List.of(ids.get(5), ids.get(10), ids.get(11), ids.get(12), ids.get(13))) {
            assertThat(count("select count(*) from lol_matches where match_id = ?", id)).as(id).isOne();
            assertThat(count("select count(*) from lol_match_participants where match_id = ?", id)).isEqualTo(10);
        }
        assertThat(count("select count(*) from lol_matches where match_id = ?", ids.get(14))).isZero();
        assertThat(count("select count(*) from lol_match_players where match_id = ? and gold_earned is not null",
                ids.get(10))).isOne();
        assertThat(count("select count(*) from lol_match_players where match_id = ? and gold_earned is not null",
                ids.get(14))).isZero();
    }

    // ---- helpers ----

    private record Account(String token, long id) {
    }

    private void storeWhole(RiotClient.Match match) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> matchStore.storeIfAbsent(match, LolPlatform.TR1));
    }

    private static boolean ownerWon(RiotClient.Match match, String puuid) {
        return match.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().orElseThrow().win();
    }

    private int count(String sql, String matchId) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, matchId);
        return count == null ? 0 : count;
    }

    private String follow(Account account, String riotId) throws Exception {
        return read(authed(post("/api/v1/lol/players"), account, riotIdBody(riotId))
                .andExpect(status().isCreated()).andReturn()).get("puuid").asString();
    }

    /** Links the account and returns its puuid, read from the caller's own member row. */
    private String linkAccount(Account account, String riotId) throws Exception {
        JsonNode members = read(authed(put(GROUP + "/me/account"), account, riotIdBody(riotId))
                .andExpect(status().isOk()).andReturn()).get("members");
        for (JsonNode member : members) {
            if (member.get("you").asBoolean()) {
                return member.get("player").get("puuid").asString();
            }
        }
        throw new AssertionError("no member row for the caller");
    }

    private Account newAccount(String displayName) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = displayName == null
                ? "{\"email\":\"" + email + "\",\"password\":\"s3cret-pass\"}"
                : "{\"email\":\"" + email + "\",\"password\":\"s3cret-pass\",\"displayName\":\"" + displayName + "\"}";
        JsonNode auth = read(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn());
        return new Account(auth.get("token").asString(), auth.get("user").get("id").asLong());
    }

    private ResultActions asUser(MockHttpServletRequestBuilder request, Account account) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token()));
    }

    private ResultActions authed(MockHttpServletRequestBuilder request, Account account, String body) throws Exception {
        return asUser(request.contentType(MediaType.APPLICATION_JSON).content(body), account);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    /** The fake derives the PUUID from the Riot ID, and the database is shared by every test, so each is unique. */
    private static String uniqueRiotId(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 6) + "#TR1";
    }

    private static String riotIdBody(String riotId) {
        return "{\"riotId\":\"" + riotId + "\",\"platform\":\"TR1\"}";
    }
}
