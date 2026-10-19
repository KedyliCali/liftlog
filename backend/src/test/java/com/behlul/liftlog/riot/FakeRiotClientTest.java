package com.behlul.liftlog.riot;

import com.behlul.liftlog.riot.RiotClient.Match;
import com.behlul.liftlog.riot.RiotClient.Participant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The generated data has to look like Riot's, or local runs would show broken pages. */
class FakeRiotClientTest {

    private final FakeRiotClient fake = new FakeRiotClient();

    private List<Match> matchesOf(String puuid, int count) {
        return fake.recentMatchIds(LolPlatform.TR1, puuid, count).stream()
                .map(id -> fake.match(LolPlatform.TR1, id).orElseThrow()).toList();
    }

    @Test
    void matchesAreWholeTenPlayerGames() {
        String puuid = fake.findAccount(LolPlatform.TR1, "Behlul", "TR1").orElseThrow().puuid();

        for (Match match : matchesOf(puuid, 6)) {
            assertThat(match.participants()).hasSize(10);
            assertThat(match.participants()).extracting(Participant::participantId)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(match.participants()).extracting(Participant::championName).doesNotHaveDuplicates();
            assertThat(match.participants()).extracting(Participant::puuid).doesNotHaveDuplicates();
            assertThat(match.teams()).hasSize(2);
            assertThat(match.teams().get(0).win()).isNotEqualTo(match.teams().get(1).win());
            assertThat(match.teams()).allSatisfy(t -> assertThat(t.bans()).hasSize(5));
            // Each side's kill count is the sum of its players' kills
            for (RiotClient.Team team : match.teams()) {
                int kills = match.participants().stream().filter(p -> p.teamId() == team.teamId())
                        .mapToInt(Participant::kills).sum();
                assertThat(team.objectives().champion().kills()).isEqualTo(kills);
            }
            Participant me = match.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().orElseThrow();
            assertThat(me.riotIdGameName()).isEqualTo("Behlul");
            assertThat(me.win()).isEqualTo(match.teams().stream().filter(t -> t.teamId() == me.teamId())
                    .findFirst().orElseThrow().win());
            assertThat(match.gameVersion()).matches("14\\.\\d+\\.\\d+\\.\\d+");
        }
    }

    @Test
    void theSameTeammateComesBackInTwoOfEveryThreeGames() {
        String puuid = "fake-duo-test";
        List<Match> matches = matchesOf(puuid, 9);

        long withDuo = matches.stream().filter(m -> {
            Participant me = m.participants().stream().filter(p -> puuid.equals(p.puuid())).findFirst().orElseThrow();
            return m.participants().stream().anyMatch(p -> p.puuid().equals(FakeRiotClient.duoOf(puuid))
                    && p.teamId().equals(me.teamId()));
        }).count();
        assertThat(withDuo).isEqualTo(6);
    }

    @Test
    void theTimelineGrowsTowardsTheScoreboard() {
        String id = fake.recentMatchIds(LolPlatform.TR1, "fake-timeline", 1).getFirst();
        Match match = fake.match(LolPlatform.TR1, id).orElseThrow();

        RiotClient.Timeline timeline = fake.timeline(LolPlatform.TR1, id).orElseThrow();

        // A frame each started minute plus the last one at the end
        assertThat(timeline.frames()).hasSize((int) Math.ceil(match.gameDuration() / 60.0) + 1);
        assertThat(timeline.frames().getLast().timestamp()).isEqualTo(match.gameDuration() * 1000);
        for (Participant p : match.participants()) {
            RiotClient.ParticipantFrame last = timeline.frames().getLast().participantFrames()
                    .get(String.valueOf(p.participantId()));
            assertThat(last.totalGold()).isEqualTo(p.goldEarned());
            assertThat(timeline.frames().getFirst().participantFrames().get(String.valueOf(p.participantId())).totalGold())
                    .isEqualTo(500);
        }
        assertThat(fake.timeline(LolPlatform.TR1, "TR1_" + FakeRiotClient.MISSING_NAME + "_1")).isEmpty();
    }

    @Test
    void aChosenTeammateLeavesTheFollowedPlayersNumbersAlone() {
        // The followed player is drawn first, so whoever else is in the match does not change their line
        Match match = FakeRiotClient.matchWith("TR1_1a2b_480", "someone", null);
        Participant me = match.participants().stream().filter(p -> "someone".equals(p.puuid())).findFirst().orElseThrow();
        Match again = FakeRiotClient.matchWith("TR1_1a2b_480", "someone", "friend");
        Participant meAgain = again.participants().stream().filter(p -> "someone".equals(p.puuid())).findFirst().orElseThrow();

        assertThat(meAgain.kills()).isEqualTo(me.kills());
        assertThat(meAgain.goldEarned()).isEqualTo(me.goldEarned());
        assertThat(meAgain.championName()).isEqualTo(me.championName());
        assertThat(again.participants()).anyMatch(p -> "friend".equals(p.puuid()) && p.teamId().equals(me.teamId()));
    }
}
