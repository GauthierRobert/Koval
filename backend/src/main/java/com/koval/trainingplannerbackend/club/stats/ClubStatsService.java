package com.koval.trainingplannerbackend.club.stats;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubExtendedStatsResponse;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.dto.ClubWeeklyStatsResponse;
import com.koval.trainingplannerbackend.club.dto.LeaderboardEntry;
import com.koval.trainingplannerbackend.club.group.ClubGroup;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplate;
import com.koval.trainingplannerbackend.club.recurring.RecurringSessionTemplateRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ClubStatsService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final RaceRepository raceRepository;
    private final UserService userService;
    private final ClubMembershipService clubMembershipService;
    private final ClubAuthorizationService authorizationService;
    private final RecurringSessionTemplateRepository recurringTemplateRepository;
    private final ClubGroupRepository clubGroupRepository;

    public ClubStatsService(ClubTrainingSessionRepository sessionRepository,
                            CompletedSessionRepository completedSessionRepository,
                            RaceGoalRepository raceGoalRepository,
                            RaceRepository raceRepository,
                            UserService userService,
                            ClubMembershipService clubMembershipService,
                            ClubAuthorizationService authorizationService,
                            RecurringSessionTemplateRepository recurringTemplateRepository,
                            ClubGroupRepository clubGroupRepository) {
        this.sessionRepository = sessionRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.raceRepository = raceRepository;
        this.userService = userService;
        this.clubMembershipService = clubMembershipService;
        this.authorizationService = authorizationService;
        this.recurringTemplateRepository = recurringTemplateRepository;
        this.clubGroupRepository = clubGroupRepository;
    }

    public ClubWeeklyStatsResponse getWeeklyStats(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<CompletedSession> sessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, weekStart, weekEnd);

        double swimKm = 0, bikeKm = 0, runKm = 0;
        for (CompletedSession s : sessions) {
            double dist = s.getBlockSummaries() != null
                    ? s.getBlockSummaries().stream()
                    .filter(b -> b.distanceMeters() != null)
                    .mapToDouble(CompletedSession.BlockSummary::distanceMeters).sum()
                    : 0;
            String sport = s.getSportType();
            if ("SWIMMING".equalsIgnoreCase(sport)) swimKm += dist / 1000.0;
            else if ("CYCLING".equalsIgnoreCase(sport)) bikeKm += dist / 1000.0;
            else if ("RUNNING".equalsIgnoreCase(sport)) runKm += dist / 1000.0;
        }
        return new ClubWeeklyStatsResponse(swimKm, bikeKm, runKm, sessions.size(), memberIds.size());
    }

    public List<LeaderboardEntry> getLeaderboard(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime weekEnd = weekStart.plusDays(7);

        List<CompletedSession> sessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, weekStart, weekEnd);

        Map<String, Double> tssMap = new LinkedHashMap<>();
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (CompletedSession s : sessions) {
            String uid = s.getUserId();
            tssMap.merge(uid, Optional.ofNullable(s.getTss()).orElse(0.0), Double::sum);
            countMap.merge(uid, 1, Integer::sum);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(tssMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> uids = sorted.stream().map(Map.Entry::getKey).toList();
        Map<String, User> userMap = userService.findAllById(uids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return IntStream.range(0, sorted.size())
                .mapToObj(i -> {
                    String uid = sorted.get(i).getKey();
                    Optional<User> uOpt = Optional.ofNullable(userMap.get(uid));
                    return new LeaderboardEntry(
                            uid,
                            uOpt.map(User::getDisplayName).orElse(uid),
                            uOpt.map(User::getProfilePicture).orElse(null),
                            sorted.get(i).getValue(),
                            countMap.getOrDefault(uid, 0),
                            i + 1);
                })
                .toList();
    }

    public ClubExtendedStatsResponse getExtendedStats(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        int memberCount = memberIds.size();

        LocalDate weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime weekEndDt = weekStartDt.plusDays(7);
        LocalDateTime fourWeeksAgo = weekStart.minusWeeks(4).atStartOfDay();
        List<LocalDate> weekStarts = buildPastFourWeekStarts(weekStart);

        List<CompletedSession> allCompletedSessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, fourWeeksAgo, weekEndDt);
        List<CompletedSession> weeklySessions = filterBetween(allCompletedSessions,
                CompletedSession::getCompletedAt, weekStartDt, weekEndDt);

        WeeklyVolume volume = computeWeeklyVolume(weeklySessions);

        List<ClubTrainingSession> allClubSessions = sessionRepository
                .findByClubIdAndScheduledAtBetween(clubId, fourWeeksAgo, weekEndDt);
        List<ClubTrainingSession> nonCancelledSessions = allClubSessions.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getCancelled())).toList();
        LocalDateTime now = LocalDateTime.now();
        List<ClubTrainingSession> pastClubSessions = nonCancelledSessions.stream()
                .filter(s -> s.getScheduledAt() != null && s.getScheduledAt().isBefore(now))
                .toList();

        int clubSessionsThisWeek = (int) nonCancelledSessions.stream()
                .filter(s -> s.getScheduledAt() != null
                        && !s.getScheduledAt().isBefore(weekStartDt)
                        && s.getScheduledAt().isBefore(weekEndDt))
                .count();
        double attendanceRate = computeAttendanceRate(pastClubSessions, memberCount);

        List<ClubExtendedStatsResponse.RecurringTemplateAttendance> recurringAttendance =
                computeRecurringAttendance(clubId, allClubSessions, memberIds, weekStarts);
        Map<String, Double> sportDistribution = computeSportDistribution(weeklySessions);
        List<ClubExtendedStatsResponse.WeeklyTrend> weeklyTrends =
                computeWeeklyTrends(weekStarts, allCompletedSessions, pastClubSessions, memberCount);
        List<ClubExtendedStatsResponse.MemberHighlight> mostActive = computeMostActive(weeklySessions);

        double avgTssPerMember = memberCount > 0 ? volume.totalTss() / memberCount : 0;
        return new ClubExtendedStatsResponse(
                volume.swimKm(), volume.bikeKm(), volume.runKm(),
                weeklySessions.size(), memberCount,
                Math.round(volume.totalDurationHours() * 10.0) / 10.0,
                Math.round(volume.totalTss() * 10.0) / 10.0,
                Math.round(attendanceRate * 1000.0) / 1000.0,
                clubSessionsThisWeek,
                recurringAttendance,
                sportDistribution, Math.round(avgTssPerMember * 10.0) / 10.0,
                weeklyTrends, mostActive);
    }

    private static List<LocalDate> buildPastFourWeekStarts(LocalDate currentWeekStart) {
        List<LocalDate> starts = new ArrayList<>(4);
        for (int w = 3; w >= 0; w--) starts.add(currentWeekStart.minusWeeks(w));
        return starts;
    }

    private static <T> List<T> filterBetween(List<T> items, Function<T, LocalDateTime> at,
                                             LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return items.stream()
                .filter(s -> at.apply(s) != null
                        && !at.apply(s).isBefore(startInclusive)
                        && at.apply(s).isBefore(endExclusive))
                .toList();
    }

    private static double computeAttendanceRate(List<ClubTrainingSession> pastSessions, int memberCount) {
        if (pastSessions.isEmpty() || memberCount <= 0) return 0;
        return pastSessions.stream()
                .mapToDouble(s -> (double) s.getParticipantIds().size() / memberCount)
                .average().orElse(0);
    }

    private static WeeklyVolume computeWeeklyVolume(List<CompletedSession> weeklySessions) {
        double swim = 0, bike = 0, run = 0, tss = 0;
        long durationSec = 0;
        for (CompletedSession s : weeklySessions) {
            double dist = s.getBlockSummaries() != null
                    ? s.getBlockSummaries().stream()
                    .filter(b -> b.distanceMeters() != null)
                    .mapToDouble(CompletedSession.BlockSummary::distanceMeters).sum()
                    : 0;
            if (dist == 0 && s.getTotalDistance() != null) dist = s.getTotalDistance();
            String sportKey = Optional.ofNullable(s.getSportType()).map(String::toUpperCase).orElse("");
            switch (sportKey) {
                case "SWIMMING" -> swim += dist / 1000.0;
                case "CYCLING" -> bike += dist / 1000.0;
                case "RUNNING" -> run += dist / 1000.0;
                default -> { /* unknown/null sport: skip distance bucket */ }
            }
            tss += Optional.ofNullable(s.getTss()).orElse(0.0);
            durationSec += s.getTotalDurationSeconds();
        }
        return new WeeklyVolume(swim, bike, run, tss, durationSec / 3600.0);
    }

    private List<ClubExtendedStatsResponse.RecurringTemplateAttendance> computeRecurringAttendance(
            String clubId, List<ClubTrainingSession> allClubSessions,
            List<String> memberIds, List<LocalDate> weekStarts) {

        List<RecurringSessionTemplate> templates = recurringTemplateRepository.findByClubId(clubId);
        Map<String, ClubGroup> groupMap = clubGroupRepository.findByClubId(clubId).stream()
                .collect(Collectors.toMap(ClubGroup::getId, g -> g));
        Map<String, List<ClubTrainingSession>> sessionsByTemplate = allClubSessions.stream()
                .filter(s -> s.getRecurringTemplateId() != null)
                .collect(Collectors.groupingBy(ClubTrainingSession::getRecurringTemplateId));

        Set<String> allEligibleIds = new HashSet<>();
        List<RecurringTemplateData> templateDataList = new ArrayList<>();
        for (RecurringSessionTemplate template : templates) {
            List<ClubTrainingSession> templateSessions = sessionsByTemplate.getOrDefault(template.getId(), List.of());
            List<String> eligibleIds = memberIds;
            String clubGroupName = null;
            if (template.getClubGroupId() != null) {
                ClubGroup group = groupMap.get(template.getClubGroupId());
                if (group != null) {
                    eligibleIds = group.getMemberIds();
                    clubGroupName = group.getName();
                }
            }
            allEligibleIds.addAll(eligibleIds);
            templateDataList.add(new RecurringTemplateData(template, templateSessions, eligibleIds, clubGroupName));
        }

        Map<String, User> eligibleUserMap = userService.findAllById(new ArrayList<>(allEligibleIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ClubExtendedStatsResponse.RecurringTemplateAttendance> result = new ArrayList<>();
        for (RecurringTemplateData data : templateDataList) {
            result.add(buildTemplateAttendance(data, weekStarts, eligibleUserMap));
        }
        return result;
    }

    private static ClubExtendedStatsResponse.RecurringTemplateAttendance buildTemplateAttendance(
            RecurringTemplateData data, List<LocalDate> weekStarts, Map<String, User> userMap) {
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("dd MMM");
        RecurringSessionTemplate template = data.template;
        Integer maxParticipants = template.getMaxParticipants();
        int eligibleCount = data.eligibleIds.size();

        List<ClubExtendedStatsResponse.WeekAttendance> weeks = new ArrayList<>();
        List<ClubTrainingSession> weeklySlots = new ArrayList<>();
        for (LocalDate wStart : weekStarts) {
            LocalDateTime wStartDt = wStart.atStartOfDay();
            LocalDateTime wEndDt = wStartDt.plusDays(7);
            ClubTrainingSession ws = data.sessions.stream()
                    .filter(s -> s.getScheduledAt() != null
                            && !s.getScheduledAt().isBefore(wStartDt)
                            && s.getScheduledAt().isBefore(wEndDt))
                    .findFirst().orElse(null);
            weeklySlots.add(ws);
            String label = wStart.format(weekFmt);
            if (ws == null) {
                weeks.add(new ClubExtendedStatsResponse.WeekAttendance(label, null, false, 0, 0, 0));
            } else {
                int participantCount = ws.getParticipantIds().size();
                int denominator = (maxParticipants != null && maxParticipants > 0) ? maxParticipants : eligibleCount;
                double fillPercent = denominator > 0
                        ? Math.round(participantCount * 1000.0 / denominator) / 10.0 : 0;
                weeks.add(new ClubExtendedStatsResponse.WeekAttendance(
                        label, ws.getId(), Boolean.TRUE.equals(ws.getCancelled()),
                        participantCount, denominator, fillPercent));
            }
        }

        List<ClubExtendedStatsResponse.AthletePresence> athleteGrid = new ArrayList<>();
        for (String athleteId : data.eligibleIds) {
            List<Boolean> presence = new ArrayList<>();
            for (ClubTrainingSession ws : weeklySlots) {
                if (ws == null || Boolean.TRUE.equals(ws.getCancelled())) presence.add(null);
                else presence.add(ws.getParticipantIds().contains(athleteId));
            }
            Optional<User> uOpt = Optional.ofNullable(userMap.get(athleteId));
            athleteGrid.add(new ClubExtendedStatsResponse.AthletePresence(
                    athleteId,
                    uOpt.map(User::getDisplayName).orElse(athleteId),
                    uOpt.map(User::getProfilePicture).orElse(null),
                    presence));
        }
        athleteGrid.sort((a, b) -> Long.compare(
                b.weekPresence().stream().filter(Boolean.TRUE::equals).count(),
                a.weekPresence().stream().filter(Boolean.TRUE::equals).count()));

        return new ClubExtendedStatsResponse.RecurringTemplateAttendance(
                template.getId(), template.getTitle(), template.getSport(),
                Optional.ofNullable(template.getDayOfWeek()).map(DayOfWeek::name).orElse(null),
                Optional.ofNullable(template.getTimeOfDay()).map(Object::toString).orElse(null),
                template.getClubGroupId(), data.clubGroupName,
                maxParticipants, eligibleCount, weeks, athleteGrid);
    }

    private static Map<String, Double> computeSportDistribution(List<CompletedSession> weeklySessions) {
        Map<String, Long> counts = weeklySessions.stream()
                .filter(s -> s.getSportType() != null)
                .collect(Collectors.groupingBy(CompletedSession::getSportType, Collectors.counting()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static List<ClubExtendedStatsResponse.WeeklyTrend> computeWeeklyTrends(
            List<LocalDate> weekStarts, List<CompletedSession> allCompletedSessions,
            List<ClubTrainingSession> pastClubSessions, int memberCount) {
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("dd MMM");
        List<ClubExtendedStatsResponse.WeeklyTrend> trends = new ArrayList<>();
        for (LocalDate wStart : weekStarts) {
            LocalDateTime wStartDt = wStart.atStartOfDay();
            LocalDateTime wEndDt = wStartDt.plusDays(7);
            List<CompletedSession> wSessions = filterBetween(
                    allCompletedSessions, CompletedSession::getCompletedAt, wStartDt, wEndDt);
            double wTss = wSessions.stream().mapToDouble(s -> Optional.ofNullable(s.getTss()).orElse(0.0)).sum();
            double wHours = wSessions.stream().mapToLong(CompletedSession::getTotalDurationSeconds).sum() / 3600.0;
            List<ClubTrainingSession> wClubSessions = filterBetween(
                    pastClubSessions, ClubTrainingSession::getScheduledAt, wStartDt, wEndDt);
            double wAttendance = computeAttendanceRate(wClubSessions, memberCount);

            String label = wStart.format(weekFmt) + " - " + wStart.plusDays(6).format(weekFmt);
            trends.add(new ClubExtendedStatsResponse.WeeklyTrend(
                    label, Math.round(wTss * 10.0) / 10.0,
                    Math.round(wHours * 10.0) / 10.0,
                    wSessions.size(), Math.round(wAttendance * 1000.0) / 1000.0));
        }
        return trends;
    }

    private List<ClubExtendedStatsResponse.MemberHighlight> computeMostActive(List<CompletedSession> weeklySessions) {
        Map<String, MemberActivity> stats = new LinkedHashMap<>();
        for (CompletedSession s : weeklySessions) {
            stats.merge(s.getUserId(),
                    new MemberActivity(s.getTotalDurationSeconds(), 1,
                            Math.round(Optional.ofNullable(s.getTss()).orElse(0.0))),
                    MemberActivity::plus);
        }
        List<String> activeIds = stats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().durationSec(), a.getValue().durationSec()))
                .limit(5).map(Map.Entry::getKey).toList();
        Map<String, User> userMap = userService.findAllById(activeIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return activeIds.stream().map(uid -> {
            MemberActivity a = stats.get(uid);
            Optional<User> uOpt = Optional.ofNullable(userMap.get(uid));
            return new ClubExtendedStatsResponse.MemberHighlight(
                    uid,
                    uOpt.map(User::getDisplayName).orElse(uid),
                    uOpt.map(User::getProfilePicture).orElse(null),
                    Math.round(a.durationSec() / 360.0) / 10.0,
                    a.count(), a.tss());
        }).toList();
    }

    private record WeeklyVolume(double swimKm, double bikeKm, double runKm,
                                double totalTss, double totalDurationHours) {}

    private record MemberActivity(long durationSec, int count, long tss) {
        MemberActivity plus(MemberActivity other) {
            return new MemberActivity(durationSec + other.durationSec,
                    count + other.count, tss + other.tss);
        }
    }

    private record RecurringTemplateData(
            RecurringSessionTemplate template,
            List<ClubTrainingSession> sessions,
            List<String> eligibleIds,
            String clubGroupName
    ) {}

    public List<ClubRaceGoalResponse> getRaceGoals(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        if (memberIds.isEmpty()) return List.of();

        String todayIso = LocalDate.now().toString();
        List<RaceGoal> allGoals = raceGoalRepository.findByAthleteIdIn(memberIds);

        // Batch-fetch every referenced race in a single Mongo round-trip. Building a
        // plain map (vs. computeIfAbsent + per-call lookups) means missing/deleted
        // raceIds resolve to a null map entry once, instead of re-hitting the DB on
        // every stream pass — the previous pattern caused the /race-goals 504s.
        Set<String> referencedRaceIds = allGoals.stream()
                .map(RaceGoal::getRaceId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, Race> raceMap = referencedRaceIds.isEmpty()
                ? Map.of()
                : raceRepository.findAllById(referencedRaceIds).stream()
                        .collect(Collectors.toMap(Race::getId, r -> r));
        Function<String, Race> resolveRace = raceId -> raceId == null ? null : raceMap.get(raceId);

        List<RaceGoal> goals = allGoals.stream()
                .filter(g -> {
                    String date = Optional.ofNullable(resolveRace.apply(g.getRaceId()))
                            .map(Race::getScheduledDate).orElse(null);
                    return date == null || date.compareTo(todayIso) >= 0;
                })
                .sorted(Comparator.comparing(g -> Optional.ofNullable(resolveRace.apply(g.getRaceId()))
                        .map(Race::getScheduledDate).orElse("9999-99-99")))
                .toList();

        Map<String, List<RaceGoal>> goalsByRace = goals.stream()
                .collect(Collectors.groupingBy(
                        g -> Optional.ofNullable(g.getRaceId()).orElseGet(() -> g.getTitle().toLowerCase().trim()),
                        LinkedHashMap::new, Collectors.toList()));

        List<String> athleteIds = goals.stream().map(RaceGoal::getAthleteId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(athleteIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return goalsByRace.values().stream().map(raceGoals -> {
            RaceGoal representative = raceGoals.getFirst();
            Race race = resolveRace.apply(representative.getRaceId());
            List<ClubRaceGoalResponse.RaceParticipant> participants = raceGoals.stream()
                    .map(g -> {
                        Optional<User> uOpt = Optional.ofNullable(userMap.get(g.getAthleteId()));
                        return new ClubRaceGoalResponse.RaceParticipant(
                                g.getAthleteId(),
                                uOpt.map(User::getDisplayName).orElse(g.getAthleteId()),
                                uOpt.map(User::getProfilePicture).orElse(null),
                                g.getPriority(),
                                g.getTargetTime());
                    })
                    .toList();

            Optional<Race> raceOpt = Optional.ofNullable(race);
            return new ClubRaceGoalResponse(
                    representative.getRaceId(),
                    raceOpt.map(Race::getTitle).orElseGet(representative::getTitle),
                    raceOpt.map(Race::getSport).filter(s -> s != null).orElseGet(representative::getSport),
                    raceOpt.map(Race::getScheduledDate).orElse(null),
                    raceOpt.map(Race::getDistance).filter(d -> d != null).orElseGet(representative::getDistance),
                    raceOpt.map(Race::getDistanceCategory).orElse(null),
                    raceOpt.map(Race::getLocation).filter(l -> l != null).orElseGet(representative::getLocation),
                    participants);
        }).toList();
    }
}
