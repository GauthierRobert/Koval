package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.notification.NotificationService;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduling business logic: workout creation, completion (with synthetic session
 * generation when no real session exists), rescheduling, and deletion.
 *
 * <p>Response-DTO enrichment (training metadata, plan-week context, club-session merging)
 * lives in {@link ScheduledWorkoutEnrichmentService}.
 */
@Service
public class ScheduleService {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final CompletedSessionRepository completedSessionRepository;
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ScheduledWorkoutEnrichmentService enrichmentService;

    public ScheduleService(ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository,
            ScheduledWorkoutService scheduledWorkoutService,
            CompletedSessionRepository completedSessionRepository,
            AnalyticsService analyticsService,
            NotificationService notificationService,
            UserRepository userRepository,
            ScheduledWorkoutEnrichmentService enrichmentService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.completedSessionRepository = completedSessionRepository;
        this.analyticsService = analyticsService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.enrichmentService = enrichmentService;
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

        if (workout.getSessionId() != null) {
            boolean isSynthetic = completedSessionRepository.findById(workout.getSessionId())
                    .map(CompletedSession::getSyntheticCompletion).orElse(false);
            if (!isSynthetic) {
                return scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                        workout.getTss(), workout.getIntensityFactor(), workout.getSessionId());
            }
            completedSessionRepository.deleteById(workout.getSessionId());
        }

        Training training = trainingRepository.findById(workout.getTrainingId()).orElse(null);
        CompletedSession session = buildSyntheticSession(workout, training);

        CompletedSession saved = completedSessionRepository.save(session);
        analyticsService.recomputeAndSaveUserLoad(workout.getAthleteId());

        ScheduledWorkout result = scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                Optional.ofNullable(saved.getTss()).map(Double::intValue).orElse(null),
                saved.getIntensityFactor(),
                saved.getId());

        notifyCoachOfCompletion(workout);

        return result;
    }

    private CompletedSession buildSyntheticSession(ScheduledWorkout workout, Training training) {
        CompletedSession session = new CompletedSession();
        session.setUserId(workout.getAthleteId());
        session.setSyntheticCompletion(true);
        session.setScheduledWorkoutId(workout.getId());
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
        return session;
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

    // ── Controller-delegated operations ───────────────────────────────────────

    public ScheduledWorkoutResponse scheduleWorkout(String userId, ScheduleRequest request) {
        ScheduledWorkout workout = new ScheduledWorkout();
        workout.setTrainingId(request.trainingId());
        workout.setAthleteId(userId);
        workout.setAssignedBy(userId);
        workout.setScheduledDate(request.scheduledDate());
        workout.setNotes(request.notes());
        workout.setStatus(ScheduleStatus.PENDING);

        ScheduledWorkout saved = scheduledWorkoutRepository.save(workout);
        return enrichmentService.enrichSingle(saved);
    }

    public List<ScheduledWorkoutResponse> getMySchedule(String userId, LocalDate start, LocalDate end,
            boolean includeClubSessions) {
        List<ScheduledWorkout> workouts = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDateBetween(userId, start.minusDays(1), end.plusDays(1));
        if (includeClubSessions) {
            return enrichmentService.getUnifiedSchedule(workouts, userId, start, end);
        }
        return enrichmentService.enrichList(workouts);
    }

    public void deleteScheduledWorkout(String userId, String id) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", id));
        if (!userId.equals(workout.getAthleteId())) {
            throw new ForbiddenOperationException("Not authorized to delete this workout");
        }
        scheduledWorkoutRepository.deleteById(id);
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
        return enrichmentService.enrichSingle(saved);
    }
}
