package com.behlul.liftlog.riot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;

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
    void missingMatchIsRiotError() {
        riot.expect(requestTo("https://europe.api.riotgames.com/lol/match/v5/matches/TR1_404"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.match(LolPlatform.TR1, "TR1_404"))
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
                                   "totalDamageDealtToChampions":25000,"win":true,"goldEarned":12000}]}}
                        """, MediaType.APPLICATION_JSON));

        RiotClient.Match match = client.match(LolPlatform.TR1, "TR1_123");
        assertThat(match.queueId()).isEqualTo(420);
        assertThat(match.participants()).singleElement().satisfies(p -> {
            assertThat(p.championName()).isEqualTo("Ahri");
            assertThat(p.totalMinionsKilled() + p.neutralMinionsKilled()).isEqualTo(192);
            assertThat(p.win()).isTrue();
        });
    }
}
