package com.behlul.liftlog;

import com.behlul.liftlog.riot.FakeRiotClient;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Live games, mastery, flex and LP changes end to end with the fake Riot client, spied on so the tests can count
 * what reached "Riot". Real security, schema and PostgreSQL (Testcontainers).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LolLiveIntegrationTest {

    private static final String PLAYERS = "/api/v1/lol/players/";
    private static final String GROUP = "/api/v1/lol/group";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private RiotClient riot;

    @Test
    void aLiveGameIsServedFromTheCacheTheSecondTime() throws Exception {
        Account me = newAccount();
        String puuid = follow(me, uniqueRiotId(FakeRiotClient.LIVE_NAME));
        JsonNode player = read(asUser(get(PLAYERS + puuid), me).andReturn()).get("player");

        JsonNode game = read(asUser(get(PLAYERS + puuid + "/live"), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueId").isNumber())
                .andExpect(jsonPath("$.gameStartTime").isNumber())
                .andExpect(jsonPath("$.fetchedAt").isNumber())
                .andExpect(jsonPath("$.participants.length()").value(10))
                .andExpect(jsonPath("$.bans.length()").value(10))
                .andReturn());
        JsonNode mine = null;
        for (JsonNode p : game.get("participants")) {
            if (puuid.equals(p.get("puuid").asString())) {
                mine = p;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("tracked").asBoolean()).isTrue();
        assertThat(mine.get("championId").asInt()).isPositive();
        assertThat(mine.get("spells").size()).isEqualTo(2);
        assertThat(mine.get("keystone").isNumber()).isTrue();
        // The followed player's rank comes from the cache; strangers get none (no extra requests)
        assertThat(mine.get("rank").isNull()).isEqualTo(player.get("rank").isNull());

        asUser(get(PLAYERS + puuid + "/live"), me).andExpect(status().isOk());
        verify(riot, times(1)).activeGame(any(), eq(puuid));
    }

    @Test
    void notInAGameIs204AndAStrangerCannotAsk() throws Exception {
        Account me = newAccount();
        String puuid = follow(me, uniqueRiotId(FakeRiotClient.OFFLINE_NAME));

        asUser(get(PLAYERS + puuid + "/live"), me).andExpect(status().isNoContent());
        asUser(get(PLAYERS + puuid + "/live"), me).andExpect(status().isNoContent());
        verify(riot, times(1)).activeGame(any(), eq(puuid));

        asUser(get(PLAYERS + puuid + "/live"), newAccount()).andExpect(status().isNotFound());
        mockMvc.perform(get(PLAYERS + puuid + "/live")).andExpect(status().isUnauthorized());
    }

    @Test
    void theGroupBoardShowsWhoIsInAGame() throws Exception {
        Account owner = newAccount();
        Account friend = newAccount();
        asUser(get(GROUP + "/live"), owner).andExpect(status().isNoContent());

        String code = read(authed(post(GROUP), owner, "{\"name\":\"Live\"}").andExpect(status().isCreated()).andReturn())
                .get("inviteCode").asString();
        authed(post(GROUP + "/join"), friend, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());
        String playing = linkAccount(owner, uniqueRiotId(FakeRiotClient.LIVE_NAME));
        String idle = linkAccount(friend, uniqueRiotId(FakeRiotClient.OFFLINE_NAME));

        JsonNode members = read(asUser(get(GROUP + "/live"), friend).andExpect(status().isOk()).andReturn()).get("members");
        assertThat(members.size()).isEqualTo(2);
        assertThat(members.get(0).get("userId").asLong()).isEqualTo(owner.id());
        assertThat(members.get(0).get("puuid").asString()).isEqualTo(playing);
        assertThat(members.get(0).get("inGame").asBoolean()).isTrue();
        assertThat(members.get(0).get("championId").asInt()).isPositive();
        assertThat(members.get(0).get("gameStartTime").isNumber()).isTrue();
        assertThat(members.get(1).get("puuid").asString()).isEqualTo(idle);
        assertThat(members.get(1).get("inGame").asBoolean()).isFalse();

        // Within two minutes the board is answered from the cache, for every member
        asUser(get(GROUP + "/live"), owner).andExpect(status().isOk());
        verify(riot, times(1)).activeGame(any(), eq(playing));
        verify(riot, times(1)).activeGame(any(), eq(idle));
    }

    @Test
    void aRefreshStoresMasteryAndFlex() throws Exception {
        Account me = newAccount();
        String puuid = follow(me, uniqueRiotId("Mastery"));

        List<RiotClient.ChampionMastery> expected = riot.championMasteries(LolPlatform.TR1, puuid);
        int score = expected.stream().mapToInt(RiotClient.ChampionMastery::championLevel).sum();
        asUser(get(PLAYERS + puuid + "/mastery"), me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(score))
                .andExpect(jsonPath("$.totalPoints").isNumber())
                .andExpect(jsonPath("$.refreshedAt").isNotEmpty())
                .andExpect(jsonPath("$.champions.length()").value(expected.size()))
                .andExpect(jsonPath("$.champions[0].championId").value((int) expected.getFirst().championId()))
                .andExpect(jsonPath("$.champions[0].level").value(expected.getFirst().championLevel()))
                .andExpect(jsonPath("$.champions[0].points").value(expected.getFirst().championPoints()))
                .andExpect(jsonPath("$.champions[0].lastPlayed").isNotEmpty());
        asUser(get(PLAYERS + puuid + "/mastery"), newAccount()).andExpect(status().isNotFound());

        RiotClient.LeagueEntry flex = riot.leagueEntries(LolPlatform.TR1, puuid).stream()
                .filter(e -> "RANKED_FLEX_SR".equals(e.queueType())).findFirst().orElse(null);
        Map<String, Object> row = jdbcTemplate.queryForMap("select flex_tier, flex_division from lol_players where puuid = ?", puuid);
        if (flex == null) {
            assertThat(row.get("flex_tier")).isNull();
            asUser(get(PLAYERS + puuid), me).andExpect(jsonPath("$.player.flexRank").isEmpty());
        } else {
            assertThat(row.get("flex_tier")).isEqualTo(flex.tier());
            asUser(get(PLAYERS + puuid), me)
                    .andExpect(jsonPath("$.player.flexRank.tier").value(flex.tier()))
                    .andExpect(jsonPath("$.player.flexRank.division").value(flex.rank()))
                    .andExpect(jsonPath("$.flexSeasonPeak.queue").value("FLEX"));
        }
    }

    @Test
    void theOneSoloGameBetweenTwoSnapshotsGetsItsLpChange() throws Exception {
        Account me = newAccount();
        // The fake leaves some players unranked and puts a quarter of games in flex: find one with two solo games
        String puuid = null;
        List<Map<String, Object>> solo = List.of();
        for (int attempt = 0; attempt < 10 && solo.size() < 2; attempt++) {
            puuid = follow(me, uniqueRiotId("Lp"));
            Integer lp = jdbcTemplate.queryForObject("select solo_lp from lol_players where puuid = ?", Integer.class, puuid);
            solo = lp == null ? List.of() : jdbcTemplate.queryForList(
                    "select match_id, ended_at, win from lol_match_players where puuid = ? and queue_id = 420 "
                            + "order by ended_at desc", puuid);
        }
        assertThat(solo).hasSizeGreaterThanOrEqualTo(2);
        Map<String, Object> player = jdbcTemplate.queryForMap(
                "select solo_tier, solo_division, solo_lp, solo_wins, solo_losses from lol_players where puuid = ?", puuid);
        String newest = (String) solo.get(0).get("match_id");
        boolean won = (Boolean) solo.get(0).get("win");
        LocalDateTime newestEnd = ((Timestamp) solo.get(0).get("ended_at")).toLocalDateTime();
        LocalDateTime secondEnd = ((Timestamp) solo.get(1).get("ended_at")).toLocalDateTime();

        // Pretend the last snapshot was taken between the two newest solo games, one game and 20 LP ago
        int wins = (Integer) player.get("solo_wins");
        int losses = (Integer) player.get("solo_losses");
        int lp = (Integer) player.get("solo_lp");
        jdbcTemplate.update("delete from lol_rank_history where puuid = ? and queue = 'SOLO'", puuid);
        jdbcTemplate.update("insert into lol_rank_history (puuid, queue, recorded_at, tier, division, lp, wins, losses) "
                        + "values (?, 'SOLO', ?, ?, ?, ?, ?, ?)", puuid,
                Timestamp.valueOf(secondEnd.plusSeconds(java.time.Duration.between(secondEnd, newestEnd).toSeconds() / 2)),
                player.get("solo_tier"), player.get("solo_division"), won ? lp - 20 : lp + 20,
                won ? wins - 1 : wins, won ? losses : losses - 1);
        jdbcTemplate.update("update lol_players set refreshed_at = ? where puuid = ?",
                // UTC, like every stored time; the local time can be hours ahead and would still count as fresh
                Timestamp.valueOf(LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1)), puuid);

        asUser(post(PLAYERS + puuid + "/refresh"), me).andExpect(status().isOk());

        int expected = won ? 20 : -20;
        assertThat(jdbcTemplate.queryForObject("select lp_change from lol_match_players where match_id = ? and puuid = ?",
                Integer.class, newest, puuid)).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject("select count(*) from lol_match_players where puuid = ? and lp_change is not null",
                Integer.class, puuid)).isOne();
        asUser(get(PLAYERS + puuid), me)
                .andExpect(jsonPath("$.matches[?(@.matchId == '" + newest + "')].lpChange").value(expected))
                .andExpect(jsonPath("$.seasonPeak.queue").value("SOLO"));
        JsonNode participants = read(asUser(get("/api/v1/lol/matches/" + newest), me).andReturn()).get("participants");
        for (JsonNode p : participants) {
            if (puuid.equals(p.get("puuid").asString())) {
                assertThat(p.get("lpChange").asInt()).isEqualTo(expected);
            } else {
                assertThat(p.get("lpChange").isNull()).isTrue();
            }
        }
    }

    // ---- helpers ----

    private record Account(String token, long id) {
    }

    private String follow(Account account, String riotId) throws Exception {
        return read(authed(post("/api/v1/lol/players"), account, riotIdBody(riotId))
                .andExpect(status().isCreated()).andReturn()).get("puuid").asString();
    }

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

    private Account newAccount() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        JsonNode auth = read(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"s3cret-pass\"}"))
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
