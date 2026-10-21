package com.behlul.liftlog.service;

import com.behlul.liftlog.dto.LolDtos;
import com.behlul.liftlog.dto.LolMatchDtos.FrameSnapshot;
import com.behlul.liftlog.dto.LolMatchDtos.LolDeepStats;
import com.behlul.liftlog.dto.LolMatchDtos.LolMatchDetail;
import com.behlul.liftlog.dto.LolMatchDtos.LolTimeline;
import com.behlul.liftlog.dto.LolMatchDtos.MatchParticipant;
import com.behlul.liftlog.dto.LolMatchDtos.MatchTeam;
import com.behlul.liftlog.dto.LolMatchDtos.TimelineMinute;
import com.behlul.liftlog.dto.LolMatchDtos.TimelineParticipant;
import com.behlul.liftlog.entity.LolMatch;
import com.behlul.liftlog.entity.LolMatchFrame;
import com.behlul.liftlog.entity.LolMatchParticipant;
import com.behlul.liftlog.entity.LolMatchPlayer;
import com.behlul.liftlog.entity.LolMatchTeam;
import com.behlul.liftlog.entity.LolPlayer;
import com.behlul.liftlog.exception.FieldValidationException;
import com.behlul.liftlog.exception.ResourceNotFoundException;
import com.behlul.liftlog.repository.LolMatchFrameRepository;
import com.behlul.liftlog.repository.LolMatchParticipantRepository;
import com.behlul.liftlog.repository.LolMatchRepository;
import com.behlul.liftlog.repository.LolMatchTeamRepository;
import com.behlul.liftlog.repository.LolPlayerRepository;
import com.behlul.liftlog.riot.LolPlatform;
import com.behlul.liftlog.riot.RiotClient;
import com.behlul.liftlog.riot.RiotClient.Frame;
import com.behlul.liftlog.riot.RiotClient.ParticipantFrame;
import com.behlul.liftlog.riot.RiotClient.Timeline;
import com.behlul.liftlog.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads built on whole stored matches: a player's detailed stats, a match page and its timeline. Match data is
 * public game data, so any signed-in user may open any stored match; a player's stats keep the player page's
 * visibility. Only the timeline ever calls Riot, once per match.
 */
@Service
public class LolMatchService {

    static final Set<Integer> GAME_WINDOWS = Set.of(20, 50, 100);
    static final int MIN_TZ_OFFSET_MINUTES = -720;
    static final int MAX_TZ_OFFSET_MINUTES = 840;
    static final int BLUE_TEAM = 100;

    private final LolService lolService;
    private final LolPlayerRepository playerRepository;
    private final LolMatchRepository matchRepository;
    private final LolMatchTeamRepository teamRepository;
    private final LolMatchParticipantRepository participantRepository;
    private final LolMatchFrameRepository frameRepository;
    private final RiotClient riot;
    private final CurrentUserProvider currentUser;

    public LolMatchService(LolService lolService, LolPlayerRepository playerRepository,
                           LolMatchRepository matchRepository, LolMatchTeamRepository teamRepository,
                           LolMatchParticipantRepository participantRepository, LolMatchFrameRepository frameRepository,
                           RiotClient riot, CurrentUserProvider currentUser) {
        this.lolService = lolService;
        this.playerRepository = playerRepository;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
        this.participantRepository = participantRepository;
        this.frameRepository = frameRepository;
        this.riot = riot;
        this.currentUser = currentUser;
    }

    /**
     * The detailed stats over the player's newest stored games in the queue. Never calls Riot: the player page
     * (loaded next to this) refreshes the player.
     *
     * @param games           20, 50 or 100
     * @param tzOffsetMinutes the viewer's offset from UTC, -720..840 (UTC-12 to UTC+14)
     */
    @Transactional(readOnly = true)
    public LolDeepStats deepStats(String puuid, LolQueueFilter queue, int games, int tzOffsetMinutes) {
        if (!GAME_WINDOWS.contains(games)) {
            throw new FieldValidationException("games", "must be 20, 50 or 100");
        }
        if (tzOffsetMinutes < MIN_TZ_OFFSET_MINUTES || tzOffsetMinutes > MAX_TZ_OFFSET_MINUTES) {
            throw new FieldValidationException("tzOffsetMinutes", "must be between -720 and 840");
        }
        LolPlayer player = lolService.loadVisible(puuid);
        List<LolMatchPlayer> lines = LolDeepStatsCalculator.select(lolService.recentMatches(player.getPuuid()), queue, games);
        List<String> ids = lines.stream().map(LolMatchPlayer::getMatchId).toList();

        Map<String, LolMatch> matches = (ids.isEmpty() ? List.<LolMatch>of() : matchRepository.findAllById(ids)).stream()
                .collect(Collectors.toMap(LolMatch::getMatchId, Function.identity()));
        Map<String, List<LolMatchTeam>> teams = byMatch(
                ids.isEmpty() ? List.of() : teamRepository.findByMatchIdIn(ids), LolMatchTeam::getMatchId);
        Map<String, List<LolMatchParticipant>> participants = byMatch(
                ids.isEmpty() ? List.of() : participantRepository.findByMatchIdIn(ids), LolMatchParticipant::getMatchId);

        List<LolDeepStatsCalculator.Game> stored = lines.stream()
                .map(line -> new LolDeepStatsCalculator.Game(line, matches.get(line.getMatchId()),
                        teams.get(line.getMatchId()), participants.get(line.getMatchId())))
                .toList();
        return LolDeepStatsCalculator.calculate(stored, queue, games, tzOffsetMinutes, visiblePuuids());
    }

    /** The full scoreboard; 404 when the match is not stored whole. */
    @Transactional(readOnly = true)
    public LolMatchDetail matchDetail(String matchId) {
        LolMatch match = loadMatch(matchId);
        Set<String> visible = visiblePuuids();
        List<MatchTeam> teams = teamRepository.findByMatchIdOrderByTeamIdAsc(matchId).stream()
                .map(t -> new MatchTeam(t.getTeamId(), t.isWin(), t.getKills(), t.getTowers(), t.getInhibitors(),
                        t.getDragons(), t.getBarons(), t.getHeralds(), t.getGrubs(), t.getAtakhan(), t.isFirstBlood(),
                        t.isFirstTower(), t.isFirstDragon(), t.isFirstBaron(), orEmpty(LolDtos.ids(t.getBans()))))
                .toList();
        Map<String, Integer> lpChanges = lolService.lpChanges(matchId);
        List<MatchParticipant> participants = participantRepository.findByMatchIdOrderByParticipantIdAsc(matchId).stream()
                .map(p -> toResponse(p, visible.contains(p.getPuuid()), lpChanges.get(p.getPuuid())))
                .toList();
        // Not fetched yet means it can still be asked for; fetched without frames means Riot no longer had it
        boolean timelineAvailable = !match.isTimelineFetched() || frameRepository.existsByMatchId(matchId);
        return new LolMatchDetail(match.getMatchId(), match.getQueueId(), match.getGameMode(), match.getGameVersion(),
                match.getEndedAt(), match.getDurationS(), teams, participants, timelineAvailable);
    }

    /**
     * The gold graph and lane differences. The first request downloads the timeline from Riot (one request) and
     * stores its frames; later ones read them. Empty when Riot no longer has it, which is remembered, so Riot is
     * not asked again. Riot being unavailable propagates (a 503) and rolls the claim back for the next try.
     *
     * @throws ResourceNotFoundException when the match is not stored whole
     */
    @Transactional
    public Optional<LolTimeline> timeline(String matchId) {
        LolMatch match = loadMatch(matchId);
        if (matchRepository.claimTimeline(matchId) == 1) {
            LolPlatform platform = LolPlatform.parse(match.getPlatform()).orElseThrow();
            Optional<Timeline> downloaded = riot.timeline(platform, matchId);
            if (downloaded.isEmpty()) {
                return Optional.empty();
            }
            frameRepository.saveAll(framesOf(matchId, downloaded.get()));
        }
        List<LolMatchFrame> frames = frameRepository.findByMatchIdOrderByMinuteAscParticipantIdAsc(matchId);
        if (frames.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, Integer> teamOf = new HashMap<>();
        participantRepository.findByMatchIdOrderByParticipantIdAsc(matchId)
                .forEach(p -> teamOf.put(p.getParticipantId(), p.getTeamId()));
        return Optional.of(toTimeline(matchId, frames, teamOf));
    }

    /**
     * Riot's frames, one a minute plus a last one at the end of the game, numbered by their place in the list: a
     * frame lands a little after each full minute, and the last one can land anywhere in a minute, so rounding
     * timestamps could give two frames the same minute.
     */
    static List<LolMatchFrame> framesOf(String matchId, Timeline timeline) {
        List<LolMatchFrame> rows = new ArrayList<>();
        List<Frame> frames = timeline.frames();
        for (int minute = 0; minute < frames.size(); minute++) {
            Map<String, ParticipantFrame> byParticipant = frames.get(minute).participantFrames();
            if (byParticipant == null) {
                continue;
            }
            for (Map.Entry<String, ParticipantFrame> e : byParticipant.entrySet()) {
                ParticipantFrame f = e.getValue();
                Integer participantId = parseId(e.getKey());
                if (f == null || participantId == null) {
                    continue;
                }
                rows.add(new LolMatchFrame(matchId, minute, participantId, zero(f.totalGold()), zero(f.xp()),
                        zero(f.minionsKilled()) + zero(f.jungleMinionsKilled()), Math.max(1, zero(f.level()))));
            }
        }
        return rows;
    }

    /** @param teamOf each participant's team; a participant the match does not list counts for red */
    static LolTimeline toTimeline(String matchId, List<LolMatchFrame> frames, Map<Integer, Integer> teamOf) {
        Map<Integer, int[]> teamGold = new TreeMap<>();
        Map<Integer, List<LolMatchFrame>> byParticipant = new TreeMap<>();
        for (LolMatchFrame f : frames) {
            int[] gold = teamGold.computeIfAbsent(f.getMinute(), m -> new int[2]);
            gold[teamOf.getOrDefault(f.getParticipantId(), 0) == BLUE_TEAM ? 0 : 1] += f.getGold();
            byParticipant.computeIfAbsent(f.getParticipantId(), p -> new ArrayList<>()).add(f);
        }
        List<TimelineMinute> minutes = teamGold.entrySet().stream()
                .map(e -> new TimelineMinute(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
        List<TimelineParticipant> participants = byParticipant.entrySet().stream()
                .map(e -> {
                    Map<Integer, LolMatchFrame> atMinute = new LinkedHashMap<>();
                    e.getValue().forEach(f -> atMinute.put(f.getMinute(), f));
                    return new TimelineParticipant(e.getKey(), snapshot(atMinute.get(10)), snapshot(atMinute.get(15)),
                            e.getValue().stream().map(LolMatchFrame::getGold).toList());
                })
                .toList();
        return new LolTimeline(matchId, minutes, participants);
    }

    private static FrameSnapshot snapshot(LolMatchFrame f) {
        return f == null ? null : new FrameSnapshot(f.getGold(), f.getCs(), f.getXp(), f.getLevel());
    }

    private LolMatch loadMatch(String matchId) {
        return matchRepository.findById(matchId).orElseThrow(() -> new ResourceNotFoundException("League match", matchId));
    }

    private Set<String> visiblePuuids() {
        return playerRepository.findVisiblePuuids(currentUser.currentUserId());
    }

    private static MatchParticipant toResponse(LolMatchParticipant p, boolean tracked, Integer lpChange) {
        return new MatchParticipant(p.getParticipantId(), p.getPuuid(), p.getRiotName(), p.getRiotTag(), p.getTeamId(),
                p.getChampion(), p.getChampLevel(), p.getPosition(), p.getKills(), p.getDeaths(), p.getAssists(),
                p.getCs(), p.getGold(), p.getDamage(), p.getDamageTaken(), p.getDamageObjectives(),
                p.getDamageBuildings(), p.getHealing(), p.getShielding(), p.getCcTime(), p.getTimeDead(),
                p.getVisionScore(), p.getWardsPlaced(), p.getWardsKilled(), p.getControlWards(), p.getDoubleKills(),
                p.getTripleKills(), p.getQuadraKills(), p.getPentaKills(), p.isFirstBlood(), p.getSoloKills(),
                p.getKillParticipation(), p.getDamageShare(), orEmpty(LolDtos.ids(p.getItems())),
                orEmpty(LolDtos.ids(p.getSummonerSpells())), p.getKeystone(), p.getPrimaryStyle(),
                p.getSecondaryStyle(), p.isWin(), tracked, lpChange);
    }

    private static <T> Map<String, List<T>> byMatch(Collection<T> rows, Function<T, String> matchId) {
        Map<String, List<T>> grouped = new HashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(matchId.apply(row), k -> new ArrayList<>()).add(row));
        return grouped;
    }

    private static List<Integer> orEmpty(List<Integer> ids) {
        return ids == null ? List.of() : ids;
    }

    private static Integer parseId(String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }
}
