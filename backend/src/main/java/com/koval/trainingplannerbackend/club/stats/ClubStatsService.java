package com.koval.trainingplannerbackend.club.stats;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubExtendedStatsResponse;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.dto.ClubWeeklyStatsResponse;
import com.koval.trainingplannerbackend.club.dto.LeaderboardEntry;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipService;
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
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ClubStatsService {

    private final ClubTrainingSessionRepository sessionRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final UserService userService;
    private final ClubMembershipService clubMembershipService;
    private final ClubAuthorizationService authorizationService;

    public ClubStatsService(ClubTrainingSessionRepository sessionRepository,
                            CompletedSessionRepository completedSessionRepository,
                            RaceGoalRepository raceGoalRepository,
                            UserService userService,
                            ClubMembershipService clubMembershipService,
                            ClubAuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.userService = userService;
        this.clubMembershipService = clubMembershipService;
        this.authorizationService = authorizationService;
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

        // Batch user lookup (N+1 fix)
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
        List<CompletedSession> weeklySessions = completedSessionRepository
                .findByUserIdInAndCompletedAtBetween(memberIds, weekStartDt, weekEndDt);

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

        // --- Attendance: club sessions past 4 weeks ---
        LocalDateTime fourWeeksAgo = weekStart.minusWeeks(4).atStartOfDay();
        List<ClubTrainingSession> clubSessions = sessionRepository
                .findByClubIdAndScheduledAtBetween(clubId, fourWeeksAgo, weekEndDt)
                .stream().filter(s -> !s.isCancelled()).toList();

        // Past sessions only (scheduled before now) for attendance calculation
        LocalDateTime now = LocalDateTime.now();
        List<ClubTrainingSession> pastClubSessions = clubSessions.stream()
                .filter(s -> s.getScheduledAt() != null && s.getScheduledAt().isBefore(now))
                .toList();

        // Club sessions this week
        int clubSessionsThisWeek = (int) clubSessions.stream()
                .filter(s -> s.getScheduledAt() != null
                        && !s.getScheduledAt().isBefore(weekStartDt)
                        && s.getScheduledAt().isBefore(weekEndDt))
                .count();

        // Attendance rate (average across past sessions)
        double attendanceRate = 0;
        if (!pastClubSessions.isEmpty() && memberCount > 0) {
            attendanceRate = pastClubSessions.stream()
                    .mapToDouble(s -> (double) s.getParticipantIds().size() / memberCount)
                    .average().orElse(0);
        }

        // Coach name lookup for recent sessions
        List<String> coachIds = pastClubSessions.stream()
                .map(ClubTrainingSession::getResponsibleCoachId)
                .filter(id -> id != null && !id.isBlank())
                .distinct().toList();
        Map<String, User> coachMap = userService.findAllById(coachIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Recent session attendance (last 10 past sessions)
        List<ClubExtendedStatsResponse.SessionAttendance> recentAttendance = pastClubSessions.stream()
                .sorted((a, b) -> b.getScheduledAt().compareTo(a.getScheduledAt()))
                .limit(10)
                .map(s -> {
                    String coachName = null;
                    if (s.getResponsibleCoachId() != null) {
                        User coach = coachMap.get(s.getResponsibleCoachId());
                        if (coach != null) coachName = coach.getDisplayName();
                    }
                    return new ClubExtendedStatsResponse.SessionAttendance(
                            s.getId(), s.getTitle(),
                            s.getScheduledAt() != null ? s.getScheduledAt().toString() : null,
                            s.getParticipantIds().size(), memberCount,
                            s.getSport(), coachName);
                })
                .toList();

        // Top attendees (past 4 weeks)
        Map<String, Integer> attendanceCounts = new LinkedHashMap<>();
        for (ClubTrainingSession s : pastClubSessions) {
            for (String pid : s.getParticipantIds()) {
                attendanceCounts.merge(pid, 1, Integer::sum);
            }
        }
        int totalPastSessions = pastClubSessions.size();

        List<String> attendeeIds = new ArrayList<>(attendanceCounts.keySet());
        Map<String, User> attendeeMap = userService.findAllById(attendeeIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ClubExtendedStatsResponse.AttendanceRanking> topAttendees = attendanceCounts.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        (double) b.getValue() / totalPastSessions,
                        (double) a.getValue() / totalPastSessions))
                .limit(10)
                .map(e -> {
                    User u = attendeeMap.get(e.getKey());
                    return new ClubExtendedStatsResponse.AttendanceRanking(
                            e.getKey(),
                            u != null ? u.getDisplayName() : e.getKey(),
                            u != null ? u.getProfilePicture() : null,
                            e.getValue(), totalPastSessions,
                            totalPastSessions > 0 ? (double) e.getValue() / totalPastSessions : 0);
                })
                .toList();

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

        // --- Weekly trends (past 4 weeks) ---
        DateTimeFormatter weekFmt = DateTimeFormatter.ofPattern("dd MMM");
        List<ClubExtendedStatsResponse.WeeklyTrend> weeklyTrends = new ArrayList<>();
        for (int w = 3; w >= 0; w--) {
            LocalDate wStart = weekStart.minusWeeks(w);
            LocalDateTime wStartDt = wStart.atStartOfDay();
            LocalDateTime wEndDt = wStartDt.plusDays(7);

            List<CompletedSession> wSessions = completedSessionRepository
                    .findByUserIdInAndCompletedAtBetween(memberIds, wStartDt, wEndDt);
            double wTss = wSessions.stream().mapToDouble(s -> s.getTss() != null ? s.getTss() : 0).sum();
            double wHours = wSessions.stream().mapToLong(CompletedSession::getTotalDurationSeconds).sum() / 3600.0;

            // Attendance for this week
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
            stats[0] += s.getTotalDurationSeconds(); // duration
            stats[1]++; // count
            stats[2] += Math.round(s.getTss() != null ? s.getTss() : 0); // tss
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
                            Math.round(stats[0] / 360.0) / 10.0, // hours with 1 decimal
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
                recentAttendance, topAttendees,
                sportDistribution, Math.round(avgTssPerMember * 10.0) / 10.0,
                weeklyTrends, mostActive);
    }

    public List<ClubRaceGoalResponse> getRaceGoals(String userId, String clubId) {
        authorizationService.requireActiveMember(userId, clubId);
        List<String> memberIds = clubMembershipService.getActiveMemberIds(clubId);
        if (memberIds.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        List<RaceGoal> goals = raceGoalRepository.findByAthleteIdInOrderByRaceDateAsc(memberIds)
                .stream()
                .filter(g -> g.getRaceDate() == null || !g.getRaceDate().isBefore(today))
                .toList();

        // Group goals by race key: raceId if present, else title+date
        Map<String, List<RaceGoal>> goalsByRace = goals.stream()
                .collect(Collectors.groupingBy(g ->
                        g.getRaceId() != null ? g.getRaceId()
                                : g.getTitle().toLowerCase().trim() + "|" + g.getRaceDate(),
                        LinkedHashMap::new, Collectors.toList()));

        // Batch-fetch users for participant info
        List<String> athleteIds = goals.stream().map(RaceGoal::getAthleteId).distinct().toList();
        Map<String, User> userMap = userService.findAllById(athleteIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // One response per unique race, sorted by date
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
                    representative.getTitle(),
                    representative.getSport(),
                    representative.getRaceDate(),
                    representative.getDistance(),
                    representative.getLocation(),
                    participants);
        }).collect(Collectors.toList());
    }
}
