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
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.plan.PlanDay;
import com.koval.trainingplannerbackend.plan.PlanWeek;
import com.koval.trainingplannerbackend.plan.TrainingPlan;
import com.koval.trainingplannerbackend.plan.TrainingPlanRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final TrainingPlanRepository planRepository;
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
            TrainingPlanRepository planRepository,
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
        this.planRepository = planRepository;
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
     * @return the updated {@link ScheduledWorkout}
     * @throws ResourceNotFoundException if the scheduled workout is not found
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId));

        // If a real (non-synthetic) session is already linked, just mark complete
        if (workout.getSessionId() != null) {
            boolean isSynthetic = completedSessionRepository.findById(workout.getSessionId())
                    .map(CompletedSession::getSyntheticCompletion).orElse(false);
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
        session.setCompletedAt(Optional.ofNullable(workout.getScheduledDate())
                .map(d -> d.atTime(12, 0))
                .orElseGet(LocalDateTime::now));

        if (training != null) {
            session.setTitle(training.getTitle());
            session.setSportType(Optional.ofNullable(training.getSportType()).map(Enum::name).orElse("CYCLING"));
            Optional.ofNullable(training.getEstimatedDurationSeconds()).ifPresent(session::setTotalDurationSeconds);
            Optional.ofNullable(training.getEstimatedTss()).map(Integer::doubleValue).ifPresent(session::setTss);
            Optional.ofNullable(training.getEstimatedIf()).ifPresent(session::setIntensityFactor);
        }

        CompletedSession saved = completedSessionRepository.save(session);
        analyticsService.recomputeAndSaveUserLoad(workout.getAthleteId());

        ScheduledWorkout result = scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                Optional.ofNullable(saved.getTss()).map(Double::intValue).orElse(null),
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
     * duration, sport, estimated TSS/IF) and plan context (plan title, week number,
     * week label) when the workout belongs to a training plan.
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

        // Batch-resolve plan context for plan-sourced workouts
        Map<String, PlanWeekInfo> planWeekIndex = buildPlanWeekIndex(workouts);

        return workouts.stream()
                .map(sw -> {
                    Optional<Training> tOpt = Optional.ofNullable(trainingsMap.get(sw.getTrainingId()));
                    Optional<PlanWeekInfo> pOpt = Optional.ofNullable(planWeekIndex.get(sw.getId()));
                    return ScheduledWorkoutResponse.from(sw,
                            tOpt.map(Training::getTitle).orElse(null),
                            tOpt.map(Training::getTrainingType).orElse(null),
                            tOpt.map(Training::getEstimatedDurationSeconds).orElse(null),
                            tOpt.map(Training::getSportType).orElse(null),
                            tOpt.map(Training::getEstimatedTss).orElse(null),
                            tOpt.map(Training::getEstimatedIf).orElse(null),
                            pOpt.map(PlanWeekInfo::planId).orElse(null),
                            pOpt.map(PlanWeekInfo::planTitle).orElse(null),
                            pOpt.map(PlanWeekInfo::weekNumber).orElse(null),
                            pOpt.map(PlanWeekInfo::weekLabel).orElse(null));
                })
                .toList();
    }

    /**
     * Enrich a single scheduled workout with training metadata and plan context.
     */
    public ScheduledWorkoutResponse enrichSingle(ScheduledWorkout sw) {
        Optional<Training> tOpt = Optional.ofNullable(sw.getTrainingId()).flatMap(trainingRepository::findById);
        Optional<PlanWeekInfo> pOpt = Optional.ofNullable(sw.getPlanId())
                .map(planId -> resolvePlanWeekInfo(planId, sw.getId()));

        return ScheduledWorkoutResponse.from(sw,
                tOpt.map(Training::getTitle).orElse(null),
                tOpt.map(Training::getTrainingType).orElse(null),
                tOpt.map(Training::getEstimatedDurationSeconds).orElse(null),
                tOpt.map(Training::getSportType).orElse(null),
                tOpt.map(Training::getEstimatedTss).orElse(null),
                tOpt.map(Training::getEstimatedIf).orElse(null),
                pOpt.map(PlanWeekInfo::planId).orElse(null),
                pOpt.map(PlanWeekInfo::planTitle).orElse(null),
                pOpt.map(PlanWeekInfo::weekNumber).orElse(null),
                pOpt.map(PlanWeekInfo::weekLabel).orElse(null));
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
        Set<String> athleteGroupIds = clubGroupRepository
                .findByClubIdInAndMemberIdsContaining(clubIds, athleteId).stream()
                .map(g -> g.getId())
                .collect(Collectors.toSet());

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
        Map<String, String> groupNameMap = clubGroupRepository.findByClubIdIn(clubIds).stream()
                .collect(Collectors.toMap(g -> g.getId(), g -> g.getName()));

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

    // --- Controller-delegated operations ---

    public ScheduledWorkoutResponse scheduleWorkout(String userId, ScheduleRequest request) {
        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(request.trainingId());
        workout.setAthleteId(userId);
        workout.setAssignedBy(userId);
        workout.setScheduledDate(request.scheduledDate());
        workout.setNotes(request.notes());
        workout.setStatus(ScheduleStatus.PENDING);

        ScheduledWorkout saved = scheduledWorkoutRepository.save(workout);
        return enrichSingle(saved);
    }

    public List<ScheduledWorkoutResponse> getMySchedule(String userId, LocalDate start, LocalDate end,
            boolean includeClubSessions) {
        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, start.minusDays(1), end.plusDays(1));
        if (includeClubSessions) {
            return getUnifiedSchedule(workouts, userId, start, end);
        }
        return enrichList(workouts);
    }

    public void deleteScheduledWorkout(String userId, String id) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", id));
        if (!userId.equals(workout.getAthleteId())) {
            throw new ForbiddenOperationException("Not authorized to delete this workout");
        }
        scheduledWorkoutRepository.deleteById(id);
    }

    // --- Plan context resolution helpers ---

    /**
     * Lightweight holder for plan context associated with a scheduled workout.
     */
    private record PlanWeekInfo(String planId, String planTitle, int weekNumber, String weekLabel) {}

    /**
     * Batch-builds a map from scheduledWorkoutId to plan context by collecting
     * distinct planIds from the workouts, fetching plans, and walking their
     * week/day structure once.
     */
    private Map<String, PlanWeekInfo> buildPlanWeekIndex(List<ScheduledWorkout> workouts) {
        List<String> planIds = workouts.stream()
                .map(ScheduledWorkout::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (planIds.isEmpty()) return Map.of();

        Map<String, TrainingPlan> plansMap = planRepository.findAllById(planIds).stream()
                .collect(Collectors.toMap(TrainingPlan::getId, Function.identity()));

        Map<String, PlanWeekInfo> index = new HashMap<>();
        for (TrainingPlan plan : plansMap.values()) {
            for (PlanWeek week : plan.getWeeks()) {
                for (PlanDay day : week.getDays()) {
                    if (day.getScheduledWorkoutId() != null) {
                        index.put(day.getScheduledWorkoutId(),
                                new PlanWeekInfo(plan.getId(), plan.getTitle(), week.getWeekNumber(), week.getLabel()));
                    }
                }
            }
        }
        return index;
    }

    /**
     * Resolves plan context for a single scheduled workout by fetching its plan
     * and walking the week/day structure.
     */
    private PlanWeekInfo resolvePlanWeekInfo(String planId, String scheduledWorkoutId) {
        return planRepository.findById(planId)
                .map(plan -> {
                    for (PlanWeek week : plan.getWeeks()) {
                        for (PlanDay day : week.getDays()) {
                            if (scheduledWorkoutId.equals(day.getScheduledWorkoutId())) {
                                return new PlanWeekInfo(plan.getId(), plan.getTitle(), week.getWeekNumber(), week.getLabel());
                            }
                        }
                    }
                    return new PlanWeekInfo(plan.getId(), plan.getTitle(), 0, null);
                })
                .orElse(null);
    }

    public ScheduledWorkoutResponse rescheduleWorkout(String userId, String id, LocalDate newDate) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", id));

        boolean isOwner = userId.equals(workout.getAthleteId());
        boolean isAssigner = userId.equals(workout.getAssignedBy());
        if (!isOwner && !isAssigner) {
            throw new ForbiddenOperationException("Not authorized to reschedule this workout");
        }
        if (workout.getStatus() != ScheduleStatus.PENDING) {
            throw new ValidationException("Only pending workouts can be rescheduled");
        }

        workout.setScheduledDate(newDate);
        ScheduledWorkout saved = scheduledWorkoutRepository.save(workout);
        return enrichSingle(saved);
    }
}
