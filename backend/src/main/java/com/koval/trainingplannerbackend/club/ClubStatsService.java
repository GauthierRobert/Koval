package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.dto.ClubRaceGoalResponse;
import com.koval.trainingplannerbackend.club.dto.ClubWeeklyStatsResponse;
import com.koval.trainingplannerbackend.club.dto.LeaderboardEntry;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClubStatsService {

    private final ClubMembershipRepository membershipRepository;
    private final ClubTrainingSessionRepository sessionRepository;
    private final CompletedSessionRepository completedSessionRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final UserService userService;

    public ClubStatsService(ClubMembershipRepository membershipRepository,
                            ClubTrainingSessionRepository sessionRepository,
                            CompletedSessionRepository completedSessionRepository,
                            RaceGoalRepository raceGoalRepository,
                            UserService userService) {
        this.membershipRepository = membershipRepository;
        this.sessionRepository = sessionRepository;
        this.completedSessionRepository = completedSessionRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.userService = userService;
    }

    public ClubWeeklyStatsResponse getWeeklyStats(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
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

    public List<LeaderboardEntry> getLeaderboard(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
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

    public List<ClubRaceGoalResponse> getRaceGoals(String clubId) {
        List<String> memberIds = getActiveMemberIds(clubId);
        if (memberIds.isEmpty()) return List.of();

        LocalDate today = LocalDate.now();
        List<RaceGoal> goals = raceGoalRepository.findByAthleteIdInOrderByRaceDateAsc(memberIds)
                .stream()
                .filter(g -> g.getRaceDate() != null && !g.getRaceDate().isBefore(today))
                .toList();
        List<ClubTrainingSession> sessions = sessionRepository.findByClubIdOrderByScheduledAtDesc(clubId);

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

            boolean hasSession = sessions.stream().anyMatch(s ->
                    s.getScheduledAt() != null &&
                    s.getScheduledAt().toLocalDate().isAfter(today) &&
                    s.getScheduledAt().toLocalDate().isBefore(representative.getRaceDate().plusDays(1)));

            // Best priority: A > B > C
            String bestPriority = raceGoals.stream()
                    .map(RaceGoal::getPriority)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse("C");

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
                    bestPriority,
                    representative.getDistance(),
                    representative.getLocation(),
                    hasSession,
                    participants);
        }).collect(Collectors.toList());
    }

    private List<String> getActiveMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).collect(Collectors.toList());
    }
}
