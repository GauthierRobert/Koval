package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.history.AnalyticsService;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final CoachService coachService;
    private final CompletedSessionRepository completedSessionRepository;
    private final AnalyticsService analyticsService;

    public ScheduleService(ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository,
            CoachService coachService,
            CompletedSessionRepository completedSessionRepository,
            AnalyticsService analyticsService) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
        this.coachService = coachService;
        this.completedSessionRepository = completedSessionRepository;
        this.analyticsService = analyticsService;
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
                return coachService.markCompleted(scheduledWorkoutId,
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

        return coachService.markCompleted(scheduledWorkoutId,
                saved.getTss() != null ? saved.getTss().intValue() : null,
                saved.getIntensityFactor(),
                saved.getId());
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
}
