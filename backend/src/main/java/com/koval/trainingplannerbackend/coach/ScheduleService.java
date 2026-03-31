package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.Club;
import com.koval.trainingplannerbackend.club.ClubRepository;
import com.koval.trainingplannerbackend.club.group.ClubGroupRepository;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service encapsulating schedule-related business logic such as marking
 * workouts completed (with synthetic session creation) and enriching
 * scheduled workouts with training metadata.
 */
@Service
public class ScheduleService {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final CompletedSessionRepository completedSessionRepository;
    private final AnalyticsService analyticsService;
    private final ClubMembershipRepository clubMembershipRepository;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;
    private final ClubRepository clubRepository;
    private final ClubGroupRepository clubGroupRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public ScheduleService(ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository,
            ScheduledWorkoutService scheduledWorkoutService,
            CompletedSessionRepository completedSessionRepository,
            AnalyticsService analyticsService,
            ClubMembershipRepository clubMembershipRepository,
            ClubTrainingSessionRepository clubTrainingSessionRepository,
            ClubRepository clubRepository,
            ClubGroupRepository clubGroupRepository,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.completedSessionRepository = completedSessionRepository;
        this.analyticsService = analyticsService;
        this.clubMembershipRepository = clubMembershipRepository;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
        this.clubRepository = clubRepository;
        this.clubGroupRepository = clubGroupRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /**
     * Mark a scheduled workout as completed.
     * <p>
     * If a real (non-synthetic) completed session is already linked, the workout
     * is simply marked complete. Otherwise a synthetic {@link CompletedSession} is
     * created from the planned training data and the user's load metrics are
     * recomputed.
     *
     * @param scheduledWorkoutId the scheduled workout to complete
     * @return the updated {@link ScheduledWorkout}, or {@code null} if not found
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId).orElse(null);
        if (workout == null) return null;

        // If a real (non-synthetic) session is already linked, just mark complete
        if (workout.getSessionId() != null) {
            boolean isSynthetic = completedSessionRepository.findById(workout.getSessionId())
                    .map(CompletedSession::isSyntheticCompletion).orElse(false);
            if (!isSynthetic) {
                return scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                        workout.getTss(), workout.getIntensityFactor(), workout.getSessionId());
            }
            // Delete existing synthetic session before creating a fresh one
            completedSessionRepository.deleteById(workout.getSessionId());
        }

        // Create a synthetic CompletedSession from planned training data
        Training training = trainingRepository.findById(workout.getTrainingId()).orElse(null);
        CompletedSession session = new CompletedSession();
        session.setUserId(workout.getAthleteId());
        session.setSyntheticCompletion(true);
        session.setScheduledWorkoutId(scheduledWorkoutId);
        session.setCompletedAt(workout.getScheduledDate() != null
                ? workout.getScheduledDate().atTime(12, 0) : LocalDateTime.now());

        if (training != null) {
            session.setTitle(training.getTitle());
            session.setSportType(training.getSportType() != null ? training.getSportType().name() : "CYCLING");
            if (training.getEstimatedDurationSeconds() != null)
                session.setTotalDurationSeconds(training.getEstimatedDurationSeconds());
            if (training.getEstimatedTss() != null)
                session.setTss(training.getEstimatedTss().doubleValue());
            if (training.getEstimatedIf() != null)
                session.setIntensityFactor(training.getEstimatedIf());
        }

        CompletedSession saved = completedSessionRepository.save(session);
        analyticsService.recomputeAndSaveUserLoad(workout.getAthleteId());

        ScheduledWorkout result = scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                saved.getTss() != null ? saved.getTss().intValue() : null,
                saved.getIntensityFactor(),
                saved.getId());

        // Notify coach that athlete completed the workout
        notifyCoachOfCompletion(workout);

        return result;
    }

    private void notifyCoachOfCompletion(ScheduledWorkout workout) {
        String coachId = workout.getAssignedBy();
        if (coachId == null || coachId.equals(workout.getAthleteId())) return;

        String athleteName = userRepository.findById(workout.getAthleteId())
                .map(User::getDisplayName).orElse("An athlete");
        String trainingTitle = trainingRepository.findById(workout.getTrainingId())
                .map(Training::getTitle).orElse("a workout");

        notificationService.sendToUsers(
                List.of(coachId),
                "Workout Completed",
                athleteName + " completed \"" + trainingTitle + "\"",
                Map.of("type", "WORKOUT_COMPLETED",
                       "athleteId", workout.getAthleteId(),
                       "trainingId", workout.getTrainingId()),
                "workoutCompletedCoach");
    }

    // --- Enrichment helpers ---

    /**
     * Enrich a list of scheduled workouts with training metadata (title, type,
     * duration, sport, estimated TSS/IF).
     */
    public List<ScheduledWorkoutResponse> enrichList(List<ScheduledWorkout> workouts) {
        if (workouts.isEmpty())
            return List.of();

        List<String> trainingIds = workouts.stream()
                .map(ScheduledWorkout::getTrainingId)
                .distinct()
                .toList();

        Map<String, Training> trainingsMap = trainingRepository.findAllById(trainingIds).stream()
                .collect(Collectors.toMap(Training::getId, Function.identity()));

        return workouts.stream()
                .map(sw -> {
                    Training t = trainingsMap.get(sw.getTrainingId());
                    String title = t != null ? t.getTitle() : null;
                    var type = t != null ? t.getTrainingType() : null;
                    Integer duration = t != null ? t.getEstimatedDurationSeconds() : null;
                    var sport = t != null ? t.getSportType() : null;
                    Integer estimatedTss = t != null ? t.getEstimatedTss() : null;
                    Double estimatedIf = t != null ? t.getEstimatedIf() : null;
                    return ScheduledWorkoutResponse.from(sw, title, type, duration, sport, estimatedTss, estimatedIf);
                })
                .toList();
    }

    /**
     * Enrich a single scheduled workout with training metadata.
     */
    public ScheduledWorkoutResponse enrichSingle(ScheduledWorkout sw) {
        return trainingRepository.findById(sw.getTrainingId())
                .map(t -> ScheduledWorkoutResponse.from(sw, t.getTitle(), t.getTrainingType(),
                        t.getEstimatedDurationSeconds(), t.getSportType(), t.getEstimatedTss(), t.getEstimatedIf()))
                .orElse(ScheduledWorkoutResponse.from(sw, null, null, null, null, null, null));
    }

    /**
     * Build a unified schedule merging regular assigned workouts with club training
     * sessions where the athlete is a participant.
     */
    public List<ScheduledWorkoutResponse> getUnifiedSchedule(
            List<ScheduledWorkout> workouts, String athleteId,
            LocalDate start, LocalDate end) {

        // 1. Enrich regular workouts
        List<ScheduledWorkoutResponse> result = new ArrayList<>(enrichList(workouts));

        // 2. Find athlete's active club memberships
        List<ClubMembership> memberships = clubMembershipRepository.findByUserId(athleteId).stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
        if (memberships.isEmpty()) {
            result.sort(Comparator.comparing(ScheduledWorkoutResponse::scheduledDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            return result;
        }

        // 3. Batch-fetch clubs for name resolution
        List<String> clubIds = memberships.stream().map(ClubMembership::getClubId).toList();
        Map<String, Club> clubMap = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(Club::getId, Function.identity()));

        // 4. Query club sessions in date range
        List<ClubTrainingSession> sessions = clubTrainingSessionRepository
                .findByClubIdInAndScheduledAtBetween(clubIds, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        // 5. Pre-fetch groups the athlete belongs to
        Set<String> athleteGroupIds = new HashSet<>();
        for (String clubId : clubIds) {
            clubGroupRepository.findByClubIdAndMemberIdsContaining(clubId, athleteId)
                    .forEach(g -> athleteGroupIds.add(g.getId()));
        }

        // 6. Filter to sessions where athlete is a participant
        List<ClubTrainingSession> relevantSessions = sessions.stream()
                .filter(s -> s.getParticipantIds().contains(athleteId))
                .filter(s -> {
                    // For group-scoped sessions, verify athlete is in that group
                    if (s.getClubGroupId() != null && !s.getClubGroupId().isBlank()) {
                        return athleteGroupIds.contains(s.getClubGroupId());
                    }
                    return true;
                })
                .toList();

        if (relevantSessions.isEmpty()) {
            result.sort(Comparator.comparing(ScheduledWorkoutResponse::scheduledDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            return result;
        }

        // 7. Batch-fetch linked trainings
        List<String> linkedTrainingIds = relevantSessions.stream()
                .map(ClubTrainingSession::getLinkedTrainingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, Training> linkedTrainings = linkedTrainingIds.isEmpty() ? Map.of()
                : trainingRepository.findAllById(linkedTrainingIds).stream()
                        .collect(Collectors.toMap(Training::getId, Function.identity()));

        // 8. Build clubGroupId → name map
        Map<String, String> groupNameMap = new HashMap<>();
        clubIds.forEach(clubId -> clubGroupRepository.findByClubId(clubId)
                .forEach(g -> groupNameMap.put(g.getId(), g.getName())));

        // 9. Map each qualifying session to a response
        for (ClubTrainingSession s : relevantSessions) {
            Club club = clubMap.get(s.getClubId());
            String clubName = club != null ? club.getName() : null;
            String clubGroupName = s.getClubGroupId() != null ? groupNameMap.get(s.getClubGroupId()) : null;
            Training linked = s.getLinkedTrainingId() != null ? linkedTrainings.get(s.getLinkedTrainingId()) : null;
            result.add(ScheduledWorkoutResponse.fromClubSession(s, clubName, clubGroupName, linked));
        }

        // 10. Sort by scheduledDate
        result.sort(Comparator.comparing(ScheduledWorkoutResponse::scheduledDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }
}
