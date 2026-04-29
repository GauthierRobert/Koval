package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition.LeaderboardSnapshot;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition.MemberHighlightSnapshot;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition.MilestoneSnapshot;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition.TopSessionSnapshot;
import com.koval.trainingplannerbackend.club.gazette.ClubGazetteEdition.WeeklyStatsSnapshot;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalRepository;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Computes the auto-curated snapshots for a gazette edition over an arbitrary
 * {@code [periodStart, periodEnd)} window.
 *
 * <p>Used in two places:
 * <ul>
 *   <li>{@code ClubGazetteService.getPayload} — live preview returned to Claude.</li>
 *   <li>{@code ClubGazettePublisher} — frozen at publish time on the edition document.</li>
 * </ul>
 */
@Service
public class ClubGazetteSnapshotService {

    private static final int LEADERBOARD_LIMIT = 10;
    private static final int TOP_SESSIONS_LIMIT = 3;
    private static final int MOST_ACTIVE_LIMIT = 5;

    private final CompletedSessionRepository completedSessionRepository;
    private final ClubTrainingSessionRepository clubSessionRepository;
    private final ClubMembershipRepository membershipRepository;
    private final RaceGoalRepository raceGoalRepository;
    private final RaceService raceService;
    private final UserService userService;

    public ClubGazetteSnapshotService(CompletedSessionRepository completedSessionRepository,
                                      ClubTrainingSessionRepository clubSessionRepository,
                                      ClubMembershipRepository membershipRepository,
                                      RaceGoalRepository raceGoalRepository,
                                      RaceService raceService,
                                      UserService userService) {
        this.completedSessionRepository = completedSessionRepository;
        this.clubSessionRepository = clubSessionRepository;
        this.membershipRepository = membershipRepository;
        this.raceGoalRepository = raceGoalRepository;
        this.raceService = raceService;
        this.userService = userService;
    }

    public WeeklyStatsSnapshot computeStats(String clubId, LocalDateTime periodStart,
                                            LocalDateTime periodEnd) {
        List<String> memberIds = activeMemberIds(clubId);
        List<CompletedSession> sessions = completedSessionsInPeriod(memberIds, periodStart, periodEnd);

        double swimKm = 0, bikeKm = 0, runKm = 0;
        double totalTss = 0;
        long totalDurationSec = 0;
        for (CompletedSession s : sessions) {
            double dist = blockDistanceKm(s);
            String sport = s.getSportType();
            if ("SWIMMING".equalsIgnoreCase(sport)) swimKm += dist;
            else if ("CYCLING".equalsIgnoreCase(sport)) bikeKm += dist;
            else if ("RUNNING".equalsIgnoreCase(sport)) runKm += dist;
            totalTss += s.getTss() != null ? s.getTss() : 0;
            totalDurationSec += s.getTotalDurationSeconds();
        }

        List<ClubTrainingSession> clubSessions = clubSessionRepository
                .findByClubIdAndScheduledAtBetween(clubId, periodStart, periodEnd)
                .stream().filter(cs -> !Boolean.TRUE.equals(cs.getCancelled())).toList();
        int memberCount = memberIds.size();
        double attendanceRate = 0;
        if (!clubSessions.isEmpty() && memberCount > 0) {
            attendanceRate = clubSessions.stream()
                    .mapToDouble(cs -> (double) cs.getParticipantIds().size() / memberCount)
                    .average().orElse(0);
        }

        return new WeeklyStatsSnapshot(
                round1(swimKm), round1(bikeKm), round1(runKm),
                sessions.size(),
                round1(totalDurationSec / 3600.0),
                round1(totalTss),
                memberCount,
                clubSessions.size(),
                round3(attendanceRate));
    }

    public List<LeaderboardSnapshot> computeLeaderboard(String clubId, LocalDateTime periodStart,
                                                        LocalDateTime periodEnd) {
        List<String> memberIds = activeMemberIds(clubId);
        List<CompletedSession> sessions = completedSessionsInPeriod(memberIds, periodStart, periodEnd);

        Map<String, double[]> agg = new HashMap<>();    // userId -> [tss, count]
        for (CompletedSession s : sessions) {
            double[] v = agg.computeIfAbsent(s.getUserId(), k -> new double[]{0, 0});
            v[0] += s.getTss() != null ? s.getTss() : 0;
            v[1] += 1;
        }

        List<Map.Entry<String, double[]>> sorted = agg.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(LEADERBOARD_LIMIT)
                .toList();

        Map<String, User> users = lookupUsers(sorted.stream().map(Map.Entry::getKey).toList());
        List<LeaderboardSnapshot> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, double[]> e = sorted.get(i);
            User u = users.get(e.getKey());
            result.add(new LeaderboardSnapshot(
                    i + 1,
                    e.getKey(),
                    u != null ? u.getDisplayName() : e.getKey(),
                    u != null ? u.getProfilePicture() : null,
                    round1(e.getValue()[0]),
                    (int) e.getValue()[1]));
        }
        return result;
    }

    public List<TopSessionSnapshot> computeTopSessions(String clubId, LocalDateTime periodStart,
                                                       LocalDateTime periodEnd) {
        List<ClubTrainingSession> sessions = clubSessionRepository
                .findByClubIdAndScheduledAtBetween(clubId, periodStart, periodEnd)
                .stream().filter(cs -> !Boolean.TRUE.equals(cs.getCancelled())).toList();

        List<ClubTrainingSession> top = sessions.stream()
                .sorted(Comparator.comparingInt((ClubTrainingSession s) -> s.getParticipantIds().size()).reversed())
                .limit(TOP_SESSIONS_LIMIT)
                .toList();
        if (top.isEmpty()) return List.of();

        List<String> allParticipantIds = top.stream()
                .flatMap(s -> s.getParticipantIds().stream())
                .distinct()
                .toList();
        Map<String, User> users = lookupUsers(allParticipantIds);

        return top.stream().map(s -> new TopSessionSnapshot(
                s.getId(),
                s.getTitle(),
                s.getSport(),
                s.getScheduledAt() != null ? s.getScheduledAt().toLocalDate() : null,
                s.getParticipantIds().size(),
                s.getParticipantIds().stream()
                        .map(uid -> {
                            User u = users.get(uid);
                            return u != null ? u.getDisplayName() : uid;
                        })
                        .toList())).toList();
    }

    public List<MemberHighlightSnapshot> computeMostActiveMembers(String clubId,
                                                                  LocalDateTime periodStart,
                                                                  LocalDateTime periodEnd) {
        List<String> memberIds = activeMemberIds(clubId);
        List<CompletedSession> sessions = completedSessionsInPeriod(memberIds, periodStart, periodEnd);

        Map<String, long[]> agg = new HashMap<>();      // userId -> [durSec, count, tss]
        for (CompletedSession s : sessions) {
            long[] v = agg.computeIfAbsent(s.getUserId(), k -> new long[3]);
            v[0] += s.getTotalDurationSeconds();
            v[1] += 1;
            v[2] += Math.round(s.getTss() != null ? s.getTss() : 0);
        }

        List<Map.Entry<String, long[]>> sorted = agg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(MOST_ACTIVE_LIMIT)
                .toList();

        Map<String, User> users = lookupUsers(sorted.stream().map(Map.Entry::getKey).toList());
        return sorted.stream().map(e -> {
            User u = users.get(e.getKey());
            long[] v = e.getValue();
            return new MemberHighlightSnapshot(
                    e.getKey(),
                    u != null ? u.getDisplayName() : e.getKey(),
                    u != null ? u.getProfilePicture() : null,
                    round1(v[0] / 3600.0),
                    (int) v[1],
                    v[2]);
        }).toList();
    }

    /**
     * v1 milestones: race finishes (RaceGoal whose linked race is in the period)
     * and club anniversaries (membership joinedAt anniversary day falls in the period).
     * PR detection is left for v2.
     */
    public List<MilestoneSnapshot> computeMilestones(String clubId, LocalDateTime periodStart,
                                                     LocalDateTime periodEnd) {
        List<MilestoneSnapshot> out = new ArrayList<>();
        List<String> memberIds = activeMemberIds(clubId);
        Map<String, User> userCache = new HashMap<>();

        // Race finishes
        List<RaceGoal> goals = raceGoalRepository.findByAthleteIdIn(memberIds);
        for (RaceGoal goal : goals) {
            LocalDate raceDate = resolveRaceDate(goal);
            if (raceDate == null) continue;
            LocalDateTime raceAt = raceDate.atStartOfDay();
            if (raceAt.isBefore(periodStart) || !raceAt.isBefore(periodEnd)) continue;
            User u = userCache.computeIfAbsent(goal.getAthleteId(),
                    id -> userService.findById(id).orElse(null));
            out.add(new MilestoneSnapshot(
                    "RACE_FINISHED",
                    goal.getAthleteId(),
                    u != null ? u.getDisplayName() : goal.getAthleteId(),
                    u != null ? u.getProfilePicture() : null,
                    "Finished " + goal.getTitle()));
        }

        // Club anniversaries
        List<ClubMembership> memberships = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        LocalDate periodStartDay = periodStart.toLocalDate();
        LocalDate periodEndDay = periodEnd.toLocalDate();
        for (ClubMembership m : memberships) {
            if (m.getJoinedAt() == null) continue;
            LocalDate joinDay = m.getJoinedAt().toLocalDate();
            int yearsAgo = periodStartDay.getYear() - joinDay.getYear();
            if (yearsAgo <= 0) continue;
            LocalDate anniversary = joinDay.plusYears(yearsAgo);
            if (anniversary.isBefore(periodStartDay) || !anniversary.isBefore(periodEndDay)) continue;
            User u = userCache.computeIfAbsent(m.getUserId(),
                    id -> userService.findById(id).orElse(null));
            out.add(new MilestoneSnapshot(
                    "CLUB_ANNIVERSARY",
                    m.getUserId(),
                    u != null ? u.getDisplayName() : m.getUserId(),
                    u != null ? u.getProfilePicture() : null,
                    yearsAgo + " year" + (yearsAgo > 1 ? "s" : "") + " in the club"));
        }
        return out;
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private List<String> activeMemberIds(String clubId) {
        return membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE)
                .stream().map(ClubMembership::getUserId).toList();
    }

    private List<CompletedSession> completedSessionsInPeriod(List<String> memberIds,
                                                             LocalDateTime start, LocalDateTime end) {
        if (memberIds.isEmpty()) return List.of();
        return completedSessionRepository.findByUserIdInAndCompletedAtBetween(memberIds, start, end);
    }

    private static double blockDistanceKm(CompletedSession s) {
        double distMeters = 0;
        if (s.getBlockSummaries() != null) {
            distMeters = s.getBlockSummaries().stream()
                    .filter(b -> b.distanceMeters() != null)
                    .mapToDouble(CompletedSession.BlockSummary::distanceMeters).sum();
        }
        if (distMeters == 0 && s.getTotalDistance() != null) {
            distMeters = s.getTotalDistance();
        }
        return distMeters / 1000.0;
    }

    private Map<String, User> lookupUsers(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        return userService.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private LocalDate resolveRaceDate(RaceGoal goal) {
        if (goal.getRaceId() == null) return null;
        try {
            Race race = raceService.getRaceById(goal.getRaceId());
            String iso = race.getScheduledDate();
            return iso == null ? null : LocalDate.parse(iso);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
