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
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClubStatsService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final UserService userService;
    private final ClubMembershipService clubMembershipService;
    private final ClubAuthorizationService authorizationService;
    private final RecurringSessionTemplateRepository recurringTemplateRepository;
    private final ClubGroupRepository clubGroupRepository;

    public ClubStatsService(ClubTrainingSessionRepository sessionRepository,
                            CompletedSessionRepository completedSessionRepository,
                            RaceGoalRepository raceGoalRepository,
                            UserService userService,
                            ClubMembershipService clubMembershipService,
                            ClubAuthorizationService authorizationService,
                            RecurringSessionTemplateRepository recurringTemplateRepository,
                            ClubGroupRepository clubGroupRepository) {
        this.sessionRepository = sessionRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
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
            tssMap.merge(uid, s.getTss() != null ? s.getTss() : 0.0, Double::sum);
            countMap.merge(uid, 1, Integer::sum);
        }

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(tssMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<String> uids = sorted.stream().map(Map.Entry::getKey).toList();
        Map<String, User> userMap = userService.findAllById(uids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            String uid = sorted.get(i).getKey();
            User user = userMap.get(uid);
            leaderboard.add(new LeaderboardEntry(
                    uid,
                    user != null ? user.getDisplayName() : uid,
                    user != null ? user.getProfilePicture() : null,
                    sorted.get(i).getValue(),
                    countMap.getOrDefault(uid, 0),
                    i + 1));
        }
        return leaderboard;
    }

    public ClubExtendedStatsResponse getExtendedStats(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        int memberCount = memberIds.size();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDateTime weekStartDt = weekStart.atStartOfDay();
        LocalDateTime weekEndDt = weekStartDt.plusDays(7);

        // --- Weekly completed sessions ---
        LocalDateTime fourWeeksAgo = weekStart.minusWeeks(4).atStartOfDay();
        List<CompletedSession> allCompletedSessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, fourWeeksAgo, weekEndDt);

        // Filter current week for volume stats
        List<CompletedSession> weeklySessions = allCompletedSessions.stream()
                .filter(s -> s.getCompletedAt() != null
                        && !s.getCompletedAt().isBefore(weekStartDt)
                        && s.getCompletedAt().isBefore(weekEndDt))
                .toList();

        double swimKm = 0, bikeKm = 0, runKm = 0, totalTss = 0;
        long totalDurationSec = 0;
        for (CompletedSession s : weeklySessions) {
            double dist = s.getBlockSummaries() != null
                    ? s.getBlockSummaries().stream()
                    .filter(b -> b.distanceMeters() != null)
                    .mapToDouble(CompletedSession.BlockSummary::distanceMeters).sum()
                    : 0;
            if (dist == 0 && s.getTotalDistance() != null) dist = s.getTotalDistance();
            String sport = s.getSportType();
            if ("SWIMMING".equalsIgnoreCase(sport)) swimKm += dist / 1000.0;
            else if ("CYCLING".equalsIgnoreCase(sport)) bikeKm += dist / 1000.0;
            else if ("RUNNING".equalsIgnoreCase(sport)) runKm += dist / 1000.0;
            totalTss += s.getTss() != null ? s.getTss() : 0;
            totalDurationSec += s.getTotalDurationSeconds();
        }
        double totalDurationHours = totalDurationSec / 3600.0;

        // --- Club sessions past 4 weeks ---
        List<ClubTrainingSession> allClubSessions = sessionRepository
                .findByClubIdAndScheduledAtBetween(clubId, fourWeeksAgo, weekEndDt);

        List<ClubTrainingSession> nonCancelledSessions = allClubSessions.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getCancelled())).toList();

        LocalDateTime now = LocalDateTime.now();
        List<ClubTrainingSession> pastClubSessions = nonCancelledSessions.stream()
                .filter(s -> s.getScheduledAt() != null && s.getScheduledAt().isBefore(now))
                .toList();

        // Club sessions this week
        int clubSessionsThisWeek = (int) nonCancelledSessions.stream()
                .filter(s -> s.getScheduledAt() != null
                        && !s.getScheduledAt().isBefore(weekStartDt)
                        && s.getScheduledAt().isBefore(weekEndDt))
                .count();

        // Overall attendance rate (past sessions)
        double attendanceRate = 0;
        if (!pastClubSessions.isEmpty() && memberCount > 0) {
            attendanceRate = pastClubSessions.stream()
                    .mapToDouble(s -> (double) s.getParticipantIds().size() / memberCount)
                    .average().orElse(0);
        }

        // --- Recurring template attendance ---
        List<RecurringSessionTemplate> templates = recurringTemplateRepository.findByClubId(clubId);
        Map<String, ClubGroup> groupMap = clubGroupRepository.findByClubId(clubId).stream()
                .collect(Collectors.toMap(ClubGroup::getId, g -> g));

        // Group all club sessions (including cancelled) by recurringTemplateId
        Map<String, List<ClubTrainingSession>> sessionsByTemplate = allClubSessions.stream()
                .filter(s -> s.getRecurringTemplateId() != null)
                .collect(Collectors.groupingBy(ClubTrainingSession::getRecurringTemplateId));

        // Build 4 week boundaries
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("dd MMM");
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int w = 3; w >= 0; w--) {
            weekStarts.add(weekStart.minusWeeks(w));
        }

        // Collect all eligible user IDs across all templates for batch lookup
        Set<String> allEligibleIds = new HashSet<>();
        List<RecurringTemplateData> templateDataList = new ArrayList<>();

        for (RecurringSessionTemplate template : templates) {
            List<ClubTrainingSession> templateSessions = sessionsByTemplate.getOrDefault(template.getId(), List.of());

            // Determine eligible members
            List<String> eligibleIds;
            String clubGroupName = null;
            if (template.getClubGroupId() != null) {
                ClubGroup group = groupMap.get(template.getClubGroupId());
                if (group != null) {
                    eligibleIds = group.getMemberIds();
                    clubGroupName = group.getName();
                } else {
                    eligibleIds = memberIds;
                }
            } else {
                eligibleIds = memberIds;
            }

            allEligibleIds.addAll(eligibleIds);
            templateDataList.add(new RecurringTemplateData(template, templateSessions, eligibleIds, clubGroupName));
        }

        // Batch user lookup
        Map<String, User> eligibleUserMap = userService.findAllById(new ArrayList<>(allEligibleIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Build recurring attendance for each template
        List<ClubExtendedStatsResponse.RecurringTemplateAttendance> recurringAttendance = new ArrayList<>();

        for (RecurringTemplateData data : templateDataList) {
            RecurringSessionTemplate template = data.template;
            List<ClubTrainingSession> templateSessions = data.sessions;
            List<String> eligibleIds = data.eligibleIds;
            int eligibleCount = eligibleIds.size();

            // Denominator for fill %
            Integer maxParticipants = template.getMaxParticipants();

            // Build week attendance
            List<ClubExtendedStatsResponse.WeekAttendance> weeks = new ArrayList<>();
            // Track sessions per week for athlete grid
            List<ClubTrainingSession> weeklySessionSlots = new ArrayList<>();

            for (LocalDate wStart : weekStarts) {
                LocalDateTime wStartDt = wStart.atStartOfDay();
                LocalDateTime wEndDt = wStartDt.plusDays(7);
                String label = wStart.format(weekFmt);

                // Find session for this week (should be 0 or 1)
                ClubTrainingSession weekSession = templateSessions.stream()
                        .filter(s -> s.getScheduledAt() != null
                                && !s.getScheduledAt().isBefore(wStartDt)
                                && s.getScheduledAt().isBefore(wEndDt))
                        .findFirst().orElse(null);

                weeklySessionSlots.add(weekSession);

                if (weekSession == null) {
                    weeks.add(new ClubExtendedStatsResponse.WeekAttendance(
                            label, null, false, 0, 0, 0));
                } else {
                    int participantCount = weekSession.getParticipantIds().size();
                    int denominator = (maxParticipants != null && maxParticipants > 0) ? maxParticipants : eligibleCount;
                    double fillPercent = denominator > 0
                            ? Math.round(participantCount * 1000.0 / denominator) / 10.0
                            : 0;
                    weeks.add(new ClubExtendedStatsResponse.WeekAttendance(
                            label, weekSession.getId(), Boolean.TRUE.equals(weekSession.getCancelled()),
                            participantCount, denominator, fillPercent));
                }
            }

            // Build athlete grid (only eligible athletes)
            List<ClubExtendedStatsResponse.AthletePresence> athleteGrid = new ArrayList<>();
            for (String athleteId : eligibleIds) {
                User user = eligibleUserMap.get(athleteId);
                List<Boolean> weekPresence = new ArrayList<>();
                for (ClubTrainingSession weekSession : weeklySessionSlots) {
                    if (weekSession == null || Boolean.TRUE.equals(weekSession.getCancelled())) {
                        weekPresence.add(null);
                    } else {
                        weekPresence.add(weekSession.getParticipantIds().contains(athleteId));
                    }
                }
                athleteGrid.add(new ClubExtendedStatsResponse.AthletePresence(
                        athleteId,
                        user != null ? user.getDisplayName() : athleteId,
                        user != null ? user.getProfilePicture() : null,
                        weekPresence));
            }

            // Sort athlete grid: most present first
            athleteGrid.sort((a, b) -> {
                long aPresent = a.weekPresence().stream().filter(Boolean.TRUE::equals).count();
                long bPresent = b.weekPresence().stream().filter(Boolean.TRUE::equals).count();
                return Long.compare(bPresent, aPresent);
            });

            recurringAttendance.add(new ClubExtendedStatsResponse.RecurringTemplateAttendance(
                    template.getId(), template.getTitle(), template.getSport(),
                    template.getDayOfWeek() != null ? template.getDayOfWeek().name() : null,
                    template.getTimeOfDay() != null ? template.getTimeOfDay().toString() : null,
                    template.getClubGroupId(), data.clubGroupName,
                    maxParticipants, eligibleCount,
                    weeks, athleteGrid));
        }

        // --- Sport distribution (this week) ---
        Map<String, Long> sportCounts = weeklySessions.stream()
                .filter(s -> s.getSportType() != null)
                .collect(Collectors.groupingBy(CompletedSession::getSportType, Collectors.counting()));
        long totalForDist = sportCounts.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> sportDistribution = sportCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> totalForDist > 0 ? Math.round(e.getValue() * 1000.0 / totalForDist) / 10.0 : 0,
                        (a, b) -> a, LinkedHashMap::new));

        // Avg TSS per member
        double avgTssPerMember = memberCount > 0 ? totalTss / memberCount : 0;

        // --- Weekly trends (past 4 weeks, using pre-fetched data) ---
        List<ClubExtendedStatsResponse.WeeklyTrend> weeklyTrends = new ArrayList<>();
        for (LocalDate wStart : weekStarts) {
            LocalDateTime wStartDt = wStart.atStartOfDay();
            LocalDateTime wEndDt = wStartDt.plusDays(7);

            List<CompletedSession> wSessions = allCompletedSessions.stream()
                    .filter(s -> s.getCompletedAt() != null
                            && !s.getCompletedAt().isBefore(wStartDt)
                            && s.getCompletedAt().isBefore(wEndDt))
                    .toList();
            double wTss = wSessions.stream().mapToDouble(s -> s.getTss() != null ? s.getTss() : 0).sum();
            double wHours = wSessions.stream().mapToLong(CompletedSession::getTotalDurationSeconds).sum() / 3600.0;

            List<ClubTrainingSession> wClubSessions = pastClubSessions.stream()
                    .filter(s -> s.getScheduledAt() != null
                            && !s.getScheduledAt().isBefore(wStartDt)
                            && s.getScheduledAt().isBefore(wEndDt))
                    .toList();
            double wAttendance = 0;
            if (!wClubSessions.isEmpty() && memberCount > 0) {
                wAttendance = wClubSessions.stream()
                        .mapToDouble(s -> (double) s.getParticipantIds().size() / memberCount)
                        .average().orElse(0);
            }

            String label = wStart.format(weekFmt) + " - " + wStart.plusDays(6).format(weekFmt);
            weeklyTrends.add(new ClubExtendedStatsResponse.WeeklyTrend(
                    label, Math.round(wTss * 10.0) / 10.0,
                    Math.round(wHours * 10.0) / 10.0,
                    wSessions.size(), Math.round(wAttendance * 1000.0) / 1000.0));
        }

        // --- Most active members (this week, top 5 by hours) ---
        Map<String, long[]> memberStats = new LinkedHashMap<>();
        for (CompletedSession s : weeklySessions) {
            long[] stats = memberStats.computeIfAbsent(s.getUserId(), k -> new long[3]);
            stats[0] += s.getTotalDurationSeconds();
            stats[1]++;
            stats[2] += Math.round(s.getTss() != null ? s.getTss() : 0);
        }
        List<String> activeMemberIds = memberStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5).map(Map.Entry::getKey).toList();
        Map<String, User> activeMemberMap = userService.findAllById(activeMemberIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ClubExtendedStatsResponse.MemberHighlight> mostActive = activeMemberIds.stream()
                .map(uid -> {
                    long[] stats = memberStats.get(uid);
                    User u = activeMemberMap.get(uid);
                    return new ClubExtendedStatsResponse.MemberHighlight(
                            uid,
                            u != null ? u.getDisplayName() : uid,
                            u != null ? u.getProfilePicture() : null,
                            Math.round(stats[0] / 360.0) / 10.0,
                            (int) stats[1], stats[2]);
                })
                .toList();

        return new ClubExtendedStatsResponse(
                swimKm, bikeKm, runKm,
                weeklySessions.size(), memberCount,
                Math.round(totalDurationHours * 10.0) / 10.0,
                Math.round(totalTss * 10.0) / 10.0,
                Math.round(attendanceRate * 1000.0) / 1000.0,
                clubSessionsThisWeek,
                recurringAttendance,
                sportDistribution, Math.round(avgTssPerMember * 10.0) / 10.0,
                weeklyTrends, mostActive);
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

        LocalDate today = LocalDate.now();
        List<RaceGoal> goals = raceGoalRepository.findByAthleteIdInOrderByRaceDateAsc(memberIds)
                .stream()
                .filter(g -> g.getRaceDate() == null || !g.getRaceDate().isBefore(today))
                .toList();

        Map<String, List<RaceGoal>> goalsByRace = goals.stream()
                .collect(Collectors.groupingBy(g ->
                        g.getRaceId() != null ? g.getRaceId()
                                : g.getTitle().toLowerCase().trim() + "|" + g.getRaceDate(),
                        LinkedHashMap::new, Collectors.toList()));

        List<String> athleteIds = goals.stream().map(RaceGoal::getAthleteId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(athleteIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return goalsByRace.values().stream().map(raceGoals -> {
            RaceGoal representative = raceGoals.getFirst();
            List<ClubRaceGoalResponse.RaceParticipant> participants = raceGoals.stream()
                    .map(g -> {
                        User u = userMap.get(g.getAthleteId());
                        return new ClubRaceGoalResponse.RaceParticipant(
                                g.getAthleteId(),
                                u != null ? u.getDisplayName() : g.getAthleteId(),
                                u != null ? u.getProfilePicture() : null,
                                g.getPriority(),
                                g.getTargetTime());
                    })
                    .toList();

            return new ClubRaceGoalResponse(
                    representative.getRaceId(),
                    representative.getTitle(),
                    representative.getSport(),
                    representative.getRaceDate(),
                    representative.getDistance(),
                    representative.getLocation(),
                    participants);
        }).collect(Collectors.toList());
    }
}
