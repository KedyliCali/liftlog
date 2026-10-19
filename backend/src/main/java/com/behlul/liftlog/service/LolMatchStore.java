package com.behlul.liftlog.service;

import com.behlul.liftlog.entity.LolMatchParticipant;
import com.behlul.liftlog.entity.LolMatchTeam;
import com.behlul.liftlog.repository.LolMatchParticipantRepository;
import com.behlul.liftlog.repository.LolMatchRepository;
import com.behlul.liftlog.repository.LolMatchTeamRepository;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient.Ban;
import com.behlul.liftlog.riot.RiotClient.Challenges;
import com.behlul.liftlog.riot.RiotClient.Match;
import com.behlul.liftlog.riot.RiotClient.Objective;
import com.behlul.liftlog.riot.RiotClient.Objectives;
import com.behlul.liftlog.riot.RiotClient.Participant;
import com.behlul.liftlog.riot.RiotClient.PerkStyle;
import com.behlul.liftlog.riot.RiotClient.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores a downloaded match whole: the match, both teams and every player. Called by the refresh for every
 * match it downloads (new or re-downloaded), inside the refresh's transaction, so it costs no extra Riot request.
 */
@Component
public class LolMatchStore {

    private static final Logger log = LoggerFactory.getLogger(LolMatchStore.class);

    private final LolMatchRepository matchRepository;
    private final LolMatchTeamRepository teamRepository;
    private final LolMatchParticipantRepository participantRepository;

    public LolMatchStore(LolMatchRepository matchRepository, LolMatchTeamRepository teamRepository,
                         LolMatchParticipantRepository participantRepository) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Stores the match unless it is stored already (another followed player was in it); true when this call
     * stored it. A match with a player Riot sent without a puuid is left out rather than stored half.
     */
    public boolean storeIfAbsent(Match match, LolPlatform platform) {
        List<Participant> players = match.participants() == null ? List.of() : match.participants();
        if (players.isEmpty() || players.stream().anyMatch(p -> p.puuid() == null)) {
            log.info("Match {} has players without a puuid; not storing it whole", match.matchId());
            return false;
        }
        int inserted = matchRepository.insertIfAbsent(match.matchId(), platform.name(), match.queueId(),
                emptyToNull(match.gameMode()), emptyToNull(match.gameVersion()), endedAt(match),
                (int) match.gameDuration());
        if (inserted == 0) {
            return false;
        }
        if (match.teams() != null) {
            teamRepository.saveAll(match.teams().stream().map(t -> team(match.matchId(), t)).toList());
        }
        List<LolMatchParticipant> rows = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            rows.add(participant(match.matchId(), i, players.get(i)));
        }
        participantRepository.saveAll(rows);
        return true;
    }

    /** The side (100 or 200) the player was on in each of these matches; matches stored only in part are absent. */
    public Map<String, Integer> teamIds(String puuid, Collection<String> matchIds) {
        if (matchIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> teamIds = new HashMap<>();
        for (LolMatchParticipant p : participantRepository.findByPuuidAndMatchIdIn(puuid, matchIds)) {
            teamIds.put(p.getMatchId(), p.getTeamId());
        }
        return teamIds;
    }

    static LocalDateTime endedAt(Match match) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(match.gameEndTimestamp()), ZoneOffset.UTC);
    }

    private static LolMatchTeam team(String matchId, Team team) {
        Objectives o = team.objectives();
        Function<Function<Objectives, Objective>, Objective> pick = f -> o == null ? null : f.apply(o);
        String bans = team.bans() == null ? null : team.bans().stream()
                .map(Ban::championId).filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
        return new LolMatchTeam(matchId, team.teamId(), team.win(),
                kills(pick.apply(Objectives::champion)), kills(pick.apply(Objectives::tower)),
                kills(pick.apply(Objectives::inhibitor)), kills(pick.apply(Objectives::dragon)),
                kills(pick.apply(Objectives::baron)), kills(pick.apply(Objectives::riftHerald)),
                kills(pick.apply(Objectives::horde)), kills(pick.apply(Objectives::atakhan)),
                first(pick.apply(Objectives::champion)), first(pick.apply(Objectives::tower)),
                first(pick.apply(Objectives::dragon)), first(pick.apply(Objectives::baron)),
                bans == null || bans.isEmpty() ? null : bans);
    }

    /**
     * @param index the player's place in Riot's list, standing in for a missing participant id (Riot numbers them
     *              in list order, starting at 1)
     */
    private static LolMatchParticipant participant(String matchId, int index, Participant p) {
        Challenges challenges = p.challenges();
        List<PerkStyle> styles = p.perks() == null || p.perks().styles() == null ? List.of() : p.perks().styles();
        Integer keystone = styles.isEmpty() || styles.getFirst().selections() == null
                || styles.getFirst().selections().isEmpty() ? null : styles.getFirst().selections().getFirst().perk();
        int teamId = p.teamId() != null ? p.teamId() : index < 5 ? 100 : 200;
        return new LolMatchParticipant(matchId, p.participantId() != null ? p.participantId() : index + 1, p.puuid(),
                emptyToNull(p.riotIdGameName()), emptyToNull(p.riotIdTagline()), teamId,
                p.championName() == null ? "Unknown" : p.championName(), zero(p.champLevel()),
                emptyToNull(p.teamPosition()), p.kills(), p.deaths(), p.assists(),
                p.totalMinionsKilled() + p.neutralMinionsKilled(), zero(p.goldEarned()),
                p.totalDamageDealtToChampions(), p.win())
                .fill(new LolMatchParticipant.Extra(zero(p.totalDamageTaken()), zero(p.damageDealtToObjectives()),
                        zero(p.damageDealtToBuildings()), zero(p.totalHeal()), zero(p.totalDamageShieldedOnTeammates()),
                        zero(p.timeCCingOthers()), zero(p.totalTimeSpentDead()), zero(p.visionScore()),
                        zero(p.wardsPlaced()), zero(p.wardsKilled()), zero(p.visionWardsBoughtInGame()),
                        zero(p.doubleKills()), zero(p.tripleKills()), zero(p.quadraKills()), zero(p.pentaKills()),
                        Boolean.TRUE.equals(p.firstBloodKill()) || Boolean.TRUE.equals(p.firstBloodAssist()),
                        challenges == null ? null : challenges.soloKills(),
                        fraction(challenges == null ? null : challenges.killParticipation()),
                        fraction(challenges == null ? null : challenges.teamDamagePercentage()),
                        itemsOf(p), spellsOf(p), keystone,
                        styles.isEmpty() ? null : styles.get(0).style(),
                        styles.size() < 2 ? null : styles.get(1).style()));
    }

    static String itemsOf(Participant p) {
        return p.items().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    static String spellsOf(Participant p) {
        return p.summoner1Id() == null || p.summoner2Id() == null ? null : p.summoner1Id() + "," + p.summoner2Id();
    }

    /** Riot sends these as doubles with many decimals; the column keeps four, and 0..1 is all it may hold. */
    static BigDecimal fraction(Double value) {
        if (value == null || value.isNaN()) {
            return null;
        }
        return BigDecimal.valueOf(Math.max(0, Math.min(1, value))).setScale(4, RoundingMode.HALF_UP);
    }

    private static int kills(Objective objective) {
        return objective == null || objective.kills() == null ? 0 : objective.kills();
    }

    private static boolean first(Objective objective) {
        return objective != null && Boolean.TRUE.equals(objective.first());
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
