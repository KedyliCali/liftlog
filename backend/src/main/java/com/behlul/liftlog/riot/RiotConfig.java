package com.behlul.liftlog.riot;

import com.behlul.liftlog.riot.RiotUnavailableException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Picks the Riot client: the real API when RIOT_API_KEY is set, fake data for local work, or "not set up". */
@Configuration
public class RiotConfig {

    private static final Logger log = LoggerFactory.getLogger(RiotConfig.class);

    @Bean
    RiotRateLimiter riotRateLimiter(Clock clock) {
        return new RiotRateLimiter(clock);
    }

    @Bean
    RiotClient riotClient(@Value("${liftlog.riot.api-key:}") String apiKey,
                          @Value("${liftlog.riot.fake:false}") boolean fake,
                          ObjectProvider<RestClient.Builder> builders, RiotRateLimiter limiter) {
        if (fake && !apiKey.isBlank()) {
            throw new IllegalStateException("Set either RIOT_API_KEY or RIOT_FAKE, not both");
        }
        if (fake) {
            log.warn("Riot API is FAKE: League data is generated, not real");
            return new FakeRiotClient();
        }
        if (!apiKey.isBlank()) {
            return new HttpRiotClient(builders.getIfAvailable(RestClient::builder), apiKey.trim(), limiter);
        }
        log.info("RIOT_API_KEY is not set; the League tracker will report that it is not configured");
        return new UnconfiguredRiotClient();
    }

    /** Every call explains that the key is missing, so the app still starts and the rest of it works. */
    static class UnconfiguredRiotClient implements RiotClient {

        private static RiotUnavailableException missing() {
            return new RiotUnavailableException(Reason.NO_KEY, "Riot API key is not configured on the server");
        }

        @Override
        public Optional<Account> findAccount(LolPlatform platform, String gameName, String tagLine) {
            throw missing();
        }

        @Override
        public Optional<Summoner> summoner(LolPlatform platform, String puuid) {
            throw missing();
        }

        @Override
        public List<LeagueEntry> leagueEntries(LolPlatform platform, String puuid) {
            throw missing();
        }

        @Override
        public List<String> recentMatchIds(LolPlatform platform, String puuid, int count) {
            throw missing();
        }

        @Override
        public Optional<Match> match(LolPlatform platform, String matchId) {
            throw missing();
        }

        @Override
        public Optional<Timeline> timeline(LolPlatform platform, String matchId) {
            throw missing();
        }

        @Override
        public List<ChampionMastery> championMasteries(LolPlatform platform, String puuid) {
            throw missing();
        }

        @Override
        public Optional<LiveGame> activeGame(LolPlatform platform, String puuid) {
            throw missing();
        }
    }
}
