package com.koval.trainingplannerbackend.coach;

import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledWorkoutService {

    private final ScheduledWorkoutRepository scheduledWorkoutRepository;

    public ScheduledWorkoutService(ScheduledWorkoutRepository scheduledWorkoutRepository) {
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
    }

    /**
     * Get an athlete's schedule within a date range.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId, LocalDate start, LocalDate end) {
        return scheduledWorkoutRepository.findByAthleteIdAndScheduledDateBetween(athleteId, start.minusDays(1),
                end.plusDays(1));
    }

    /**
     * Get all scheduled workouts for an athlete.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId) {
        return scheduledWorkoutRepository.findByAthleteId(athleteId);
    }

    /**
     * Mark a scheduled workout as completed.
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor,
            String sessionId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.COMPLETED);
        workout.setCompletedAt(LocalDateTime.now());
        if (tss != null)
            workout.setTss(tss);
        if (intensityFactor != null)
            workout.setIntensityFactor(intensityFactor);
        if (sessionId != null)
            workout.setSessionId(sessionId);

        return scheduledWorkoutRepository.save(workout);
    }

    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor) {
        return markCompleted(scheduledWorkoutId, tss, intensityFactor, null);
    }

    /**
     * Mark a scheduled workout as skipped.
     */
    public ScheduledWorkout markSkipped(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Scheduled workout", scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.SKIPPED);
        return scheduledWorkoutRepository.save(workout);
    }
}
