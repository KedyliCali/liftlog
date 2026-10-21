package com.behlul.liftlog.riot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The real client against a mocked Riot: right hosts, key header, JSON mapping and error translation. */
class HttpRiotClientTest {

    private MockRestServiceServer riot;
    private HttpRiotClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        riot = MockRestServiceServer.bindTo(builder).build();
        client = new HttpRiotClient(builder, "RGAPI-test", new RiotRateLimiter(Clock.systemUTC()));
    }

    @Test
    void findsAccountsOnTheRegionalHostAndEncodesTheName() {
        riot.expect(requestTo("https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/Behl%C3%BCl%20K/TR1"))
                .andExpect(header("X-Riot-Token", "RGAPI-test"))
                .andRespond(withSuccess("{\"puuid\":\"abc\",\"gameName\":\"Behlül K\",\"tagLine\":\"TR1\",\"extra\":1}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.findAccount(LolPlatform.TR1, "Behlül K", "TR1"))
                .hasValueSatisfying(a -> assertThat(a.puuid()).isEqualTo("abc"));
        riot.verify();
    }

    @Test
    void unknownRiotIdIsEmptyNotAnError() {
        riot.expect(requestTo("https://europe.api.riotgames.com/riot/account/v1/accounts/by-riot-id/nobody/TR1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findAccount(LolPlatform.TR1, "nobody", "TR1")).isEmpty();
    }

    @Test
    void summonerNotFoundIsEmpty() {
        // What a Riot ID picked with the wrong server looks like: the account exists, the profile does not
        riot.expect(requestTo("https://euw1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/abc"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.summoner(LolPlatform.EUW1, "abc")).isEmpty();
    }

    @Test
    void missingMatchIsEmpty() {
        // Riot's match list can still name a match it no longer serves
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.match(LolPlatform.TR1, "TR1_404")).isEmpty();
    }

    @Test
    void failingMatchIsRiotError() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_500"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.match(LolPlatform.TR1, "TR1_500"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RIOT_ERROR));
    }

    @Test
    void matchWithoutInfoIsRiotError() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_1"))
                .andRespond(withSuccess("{\"metadata\":{\"matchId\":\"TR1_1\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.match(LolPlatform.TR1, "TR1_1"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RIOT_ERROR));
    }

    @Test
    void rejectedKeyAndRateLimitBecomeUnavailable() {
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/abc"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.summoner(LolPlatform.TR1, "abc"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.KEY_REJECTED));

        riot.reset();
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/league/v4/entries/by-puuid/abc"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.leagueEntries(LolPlatform.TR1, "abc"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RATE_LIMITED));
    }

    @Test
    void readsMatchMetadataAndParticipants() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_123"))
                .andRespond(withSuccess("""
                        {"metadata":{"matchId":"TR1_123","participants":["abc"]},
                         "info":{"queueId":420,"gameEndTimestamp":1760000000000,"gameDuration":1800,"gameMode":"CLASSIC",
                                 "participants":[{"puuid":"abc","championName":"Ahri","teamPosition":"MIDDLE","kills":7,
                                   "deaths":2,"assists":9,"totalMinionsKilled":180,"neutralMinionsKilled":12,
                                   "totalDamageDealtToChampions":25000,"win":true,"goldEarned":12000,
                                   "visionScore":31,"champLevel":17,"totalDamageTaken":18000,"largestMultiKill":3,
                                   "wardsPlaced":11,"item0":3006,"item1":3031,"item2":0,"item3":3071,"item4":6672,
                                   "item5":3153,"item6":3363,"summoner1Id":4,"summoner2Id":14,
                                   "challenges":{"killParticipation":0.6153846153846154,"teamDamagePercentage":0.31,
                                                 "kda":8.0}}]}}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.Match match = client.match(LolPlatform.TR1, "TR1_123").orElseThrow();
        assertThat(match.queueId()).isEqualTo(420);
        assertThat(match.gameMode()).isEqualTo("CLASSIC");
        assertThat(match.participants()).singleElement().satisfies(p -> {
            assertThat(p.championName()).isEqualTo("Ahri");
            assertThat(p.totalMinionsKilled() + p.neutralMinionsKilled()).isEqualTo(192);
            assertThat(p.win()).isTrue();
            assertThat(p.goldEarned()).isEqualTo(12000);
            assertThat(p.visionScore()).isEqualTo(31);
            assertThat(p.champLevel()).isEqualTo(17);
            assertThat(p.totalDamageTaken()).isEqualTo(18000);
            assertThat(p.largestMultiKill()).isEqualTo(3);
            assertThat(p.wardsPlaced()).isEqualTo(11);
            assertThat(p.items()).containsExactly(3006, 3031, 0, 3071, 6672, 3153, 3363);
            assertThat(p.summoner1Id()).isEqualTo(4);
            assertThat(p.summoner2Id()).isEqualTo(14);
            assertThat(p.challenges().killParticipation()).isEqualTo(0.6153846153846154);
            assertThat(p.challenges().teamDamagePercentage()).isEqualTo(0.31);
        });
    }

    @Test
    void readsTeamsAndTheWholeScoreboard() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_9"))
                .andRespond(withSuccess("""
                        {"metadata":{"matchId":"TR1_9"},
                         "info":{"queueId":420,"gameEndTimestamp":1760000000000,"gameDuration":1800,"gameMode":"CLASSIC",
                                 "gameVersion":"14.20.612.3456",
                                 "teams":[{"teamId":100,"win":true,"bans":[{"championId":266,"pickTurn":1},{"championId":-1}],
                                           "objectives":{"baron":{"first":true,"kills":1},"champion":{"first":false,"kills":30},
                                                         "dragon":{"first":true,"kills":3},"horde":{"first":false,"kills":4},
                                                         "inhibitor":{"first":true,"kills":1},"riftHerald":{"first":false,"kills":1},
                                                         "tower":{"first":true,"kills":9}}}],
                                 "participants":[{"puuid":"abc","championName":"Ahri","teamPosition":"MIDDLE","kills":7,
                                   "deaths":2,"assists":9,"totalMinionsKilled":180,"neutralMinionsKilled":12,
                                   "totalDamageDealtToChampions":25000,"win":true,"participantId":3,
                                   "riotIdGameName":"Behlul","riotIdTagline":"TR1","teamId":100,
                                   "damageDealtToObjectives":5000,"damageDealtToBuildings":3000,"totalHeal":2000,
                                   "totalDamageShieldedOnTeammates":100,"timeCCingOthers":12,"totalTimeSpentDead":40,
                                   "wardsKilled":3,"visionWardsBoughtInGame":2,"doubleKills":1,"tripleKills":0,
                                   "quadraKills":0,"pentaKills":0,"firstBloodKill":false,"firstBloodAssist":true,
                                   "challenges":{"soloKills":2},
                                   "perks":{"styles":[{"style":8100,"selections":[{"perk":8112},{"perk":8139}]},
                                                      {"style":8200,"selections":[{"perk":8210}]}]}}]}}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.Match match = client.match(LolPlatform.TR1, "TR1_9").orElseThrow();
        assertThat(match.gameVersion()).isEqualTo("14.20.612.3456");
        assertThat(match.teams()).singleElement().satisfies(t -> {
            assertThat(t.teamId()).isEqualTo(100);
            assertThat(t.bans()).extracting(RiotClient.Ban::championId).containsExactly(266, -1);
            assertThat(t.objectives().horde().kills()).isEqualTo(4);
            assertThat(t.objectives().tower().first()).isTrue();
            // Matches from before Atakhan existed have none
            assertThat(t.objectives().atakhan()).isNull();
        });
        assertThat(match.participants()).singleElement().satisfies(p -> {
            assertThat(p.participantId()).isEqualTo(3);
            assertThat(p.riotIdGameName()).isEqualTo("Behlul");
            assertThat(p.teamId()).isEqualTo(100);
            assertThat(p.timeCCingOthers()).isEqualTo(12);
            assertThat(p.visionWardsBoughtInGame()).isEqualTo(2);
            assertThat(p.firstBloodAssist()).isTrue();
            assertThat(p.challenges().soloKills()).isEqualTo(2);
            assertThat(p.perks().styles().getFirst().selections().getFirst().perk()).isEqualTo(8112);
            assertThat(p.perks().styles().get(1).style()).isEqualTo(8200);
        });
    }

    @Test
    void readsTheTimelineFrames() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_9/timeline"))
                .andExpect(header("X-Riot-Token", "RGAPI-test"))
                .andRespond(withSuccess("""
                        {"metadata":{"matchId":"TR1_9"},
                         "info":{"frameInterval":60000,"frames":[
                           {"timestamp":0,"events":[],"participantFrames":{"1":{"totalGold":500,"xp":0,"minionsKilled":0,
                             "jungleMinionsKilled":0,"level":1,"championStats":{"armor":30}}}},
                           {"timestamp":60021,"participantFrames":{"1":{"totalGold":620,"xp":180,"minionsKilled":3,
                             "jungleMinionsKilled":1,"level":1}}}]}}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.Timeline timeline = client.timeline(LolPlatform.TR1, "TR1_9").orElseThrow();
        assertThat(timeline.frames()).hasSize(2);
        assertThat(timeline.frames().get(1).timestamp()).isEqualTo(60021);
        RiotClient.ParticipantFrame frame = timeline.frames().get(1).participantFrames().get("1");
        assertThat(frame.totalGold()).isEqualTo(620);
        assertThat(frame.minionsKilled() + frame.jungleMinionsKilled()).isEqualTo(4);
    }

    @Test
    void missingTimelineIsEmptyAndAFailingOneIsRiotError() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_404/timeline"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(client.timeline(LolPlatform.TR1, "TR1_404")).isEmpty();

        riot.reset();
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_500/timeline"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.timeline(LolPlatform.TR1, "TR1_500"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RIOT_ERROR));
    }

    @Test
    void missingChallengesAreNull() {
        // Some modes send no "challenges" at all, others leave single values out
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_7"))
                .andRespond(withSuccess("""
                        {"metadata":{"matchId":"TR1_7"},
                         "info":{"queueId":450,"gameEndTimestamp":1760000000000,"gameDuration":1100,"gameMode":"ARAM",
                                 "participants":[{"puuid":"abc","championName":"Lux","teamPosition":"","kills":3,
                                                  "deaths":4,"assists":20,"totalMinionsKilled":30,"neutralMinionsKilled":0,
                                                  "totalDamageDealtToChampions":21000,"win":false,"item0":3089},
                                                 {"puuid":"def","championName":"Jinx","teamPosition":"","kills":9,
                                                  "deaths":5,"assists":11,"totalMinionsKilled":60,"neutralMinionsKilled":0,
                                                  "totalDamageDealtToChampions":33000,"win":true,"goldEarned":11000,
                                                  "challenges":{"kda":4.0}}]}}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.Match match = client.match(LolPlatform.TR1, "TR1_7").orElseThrow();
        assertThat(match.gameMode()).isEqualTo("ARAM");
        assertThat(match.participants().get(0).challenges()).isNull();
        // Detail numbers a mode leaves out are null, not a failed match; missing item slots read as empty
        assertThat(match.participants().get(0).goldEarned()).isNull();
        assertThat(match.participants().get(0).items()).containsExactly(3089, 0, 0, 0, 0, 0, 0);
        assertThat(match.participants().get(1).goldEarned()).isEqualTo(11000);
        assertThat(match.participants().get(1).challenges().killParticipation()).isNull();
        assertThat(match.participants().get(1).challenges().teamDamagePercentage()).isNull();
    }

    @Test
    void masteryComesFromThePlatformHost() {
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/champion-mastery/v5/champion-masteries/by-puuid/abc"))
                .andExpect(header("X-Riot-Token", "RGAPI-test"))
                .andRespond(withSuccess("""
                        [{"puuid":"abc","championId":103,"championLevel":42,"championPoints":512345,
                          "lastPlayTime":1760000000000,"championPointsSinceLastLevel":1000,"markRequiredForNextLevel":2},
                         {"puuid":"abc","championId":222,"championLevel":7,"championPoints":60000,"lastPlayTime":1750000000000}]
                        """, MediaType.APPLICATION_JSON));

        List<RiotClient.ChampionMastery> masteries = client.championMasteries(LolPlatform.TR1, "abc");

        assertThat(masteries).hasSize(2);
        assertThat(masteries.getFirst().championId()).isEqualTo(103);
        assertThat(masteries.getFirst().championLevel()).isEqualTo(42);
        assertThat(masteries.getFirst().championPoints()).isEqualTo(512_345);
        assertThat(masteries.getFirst().lastPlayTime()).isEqualTo(1_760_000_000_000L);
        riot.verify();
    }

    @Test
    void masteryOfAnUnknownPlayerIsEmpty() {
        riot.expect(requestTo("https://euw1.api.riotgames.com/lol/champion-mastery/v5/champion-masteries/by-puuid/nobody"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.championMasteries(LolPlatform.EUW1, "nobody")).isEmpty();
    }

    @Test
    void liveGameIsMappedFromSpectatorV5() {
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/spectator/v5/active-games/by-summoner/abc"))
                .andRespond(withSuccess("""
                        {"gameId":123,"mapId":11,"gameMode":"CLASSIC","gameType":"MATCHED","gameQueueConfigId":420,
                         "gameStartTime":1760000000000,"gameLength":615,"platformId":"TR1",
                         "participants":[{"puuid":"abc","teamId":100,"spell1Id":4,"spell2Id":14,"championId":103,
                                          "profileIconId":1,"riotId":"Behlul#TR1","bot":false,
                                          "perks":{"perkIds":[8112,8126,8138],"perkStyle":8100,"perkSubStyle":8300}},
                                         {"puuid":"def","teamId":200,"spell1Id":4,"spell2Id":11,"championId":64,
                                          "riotId":"Other#EUW"}],
                         "bannedChampions":[{"championId":157,"teamId":100,"pickTurn":1},
                                            {"championId":-1,"teamId":200,"pickTurn":2}]}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.LiveGame game = client.activeGame(LolPlatform.TR1, "abc").orElseThrow();

        assertThat(game.gameQueueConfigId()).isEqualTo(420);
        assertThat(game.gameStartTime()).isEqualTo(1_760_000_000_000L);
        assertThat(game.gameLength()).isEqualTo(615);
        assertThat(game.participants()).hasSize(2);
        RiotClient.LiveParticipant me = game.participants().getFirst();
        assertThat(me.riotId()).isEqualTo("Behlul#TR1");
        assertThat(me.championId()).isEqualTo(103);
        assertThat(me.keystone()).isEqualTo(8112);
        assertThat(me.perks().perkSubStyle()).isEqualTo(8300);
        // No perks at all (some modes) is no keystone, not a failure
        assertThat(game.participants().get(1).keystone()).isNull();
        assertThat(game.bannedChampions()).extracting(RiotClient.LiveBan::championId).containsExactly(157, -1);
    }

    @Test
    void notInAGameIsEmpty() {
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/spectator/v5/active-games/by-summoner/abc"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.activeGame(LolPlatform.TR1, "abc")).isEmpty();
    }

    @Test
    void liveGameErrorsAreRiotErrors() {
        riot.expect(requestTo("https://tr1.api.riotgames.com/lol/spectator/v5/active-games/by-summoner/abc"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.activeGame(LolPlatform.TR1, "abc"))
                .isInstanceOfSatisfying(RiotUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(RiotUnavailableException.Reason.RATE_LIMITED));
    }
}
