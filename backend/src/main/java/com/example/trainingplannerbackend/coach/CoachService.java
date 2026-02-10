package com.example.trainingplannerbackend.coach;

import com.example.trainingplannerbackend.auth.User;
import com.example.trainingplannerbackend.auth.UserRole;
import com.example.trainingplannerbackend.auth.UserRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for Coach-specific operations.
 * These methods are designed to be exposed to the AI model via function calling
 * (coach role only).
 */
@Service
public class CoachService {

    private final UserRepository userRepository;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;

    public CoachService(UserRepository userRepository, ScheduledWorkoutRepository scheduledWorkoutRepository) {
        this.userRepository = userRepository;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
    }

    /**
     * Assign a training to one or more athletes.
     */
    @Tool(description = "Assign a training plan to one or more athletes on a specific date")
    public List<ScheduledWorkout> assignTraining(
            String coachId,
            String trainingId,
            List<String> athleteIds,
            LocalDate scheduledDate,
            String notes) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));

        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("User is not a coach: " + coachId);
        }

        List<ScheduledWorkout> assignments = new ArrayList<>();
        for (String athleteId : athleteIds) {
            // Verify athlete exists and is assigned to this coach
            User athlete = userRepository.findById(athleteId)
                    .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

            if (!coachId.equals(athlete.getCoachId())) {
                throw new IllegalStateException("Athlete " + athleteId + " is not assigned to coach " + coachId);
            }

            ScheduledWorkout workout = new ScheduledWorkout();
            workout.setTrainingId(trainingId);
            workout.setAthleteId(athleteId);
            workout.setAssignedBy(coachId);
            workout.setScheduledDate(scheduledDate);
            workout.setNotes(notes);
            workout.setStatus(ScheduleStatus.PENDING);

            assignments.add(scheduledWorkoutRepository.save(workout));
        }

        return assignments;
    }

    /**
     * Unassign a scheduled workout.
     */
    @Tool(description = "Unassign a scheduled workout by its Id")
    public void unassignTraining(String scheduledWorkoutId) {
        if (!scheduledWorkoutRepository.existsById(scheduledWorkoutId)) {
            throw new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId);
        }
        scheduledWorkoutRepository.deleteById(scheduledWorkoutId);
    }

    /**
     * Get an athlete's schedule within a date range.
     */
    @Tool(description = "Get the training schedule for a specific athlete within a date range")
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId, LocalDate start, LocalDate end) {
        return scheduledWorkoutRepository.findByAthleteIdAndScheduledDateBetween(athleteId, start, end);
    }

    /**
     * Get all scheduled workouts for an athlete.
     */
    public List<ScheduledWorkout> getAthleteSchedule(String athleteId) {
        return scheduledWorkoutRepository.findByAthleteId(athleteId);
    }

    /**
     * Get all athletes for a coach.
     */
    @Tool(description = "Get the list of athletes assigned to a specific coach")
    public List<User> getCoachAthletes(String coachId) {
        return userRepository.findByCoachId(coachId);
    }

    /**
     * Add an athlete to a coach's roster.
     */
    public void addAthlete(String coachId, String athleteId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));
        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        if (coach.getRole() != UserRole.COACH) {
            throw new IllegalStateException("User is not a coach: " + coachId);
        }

        // Set the athlete's coach
        athlete.setCoachId(coachId);
        userRepository.save(athlete);

        // Add to coach's athlete list
        if (!coach.getAthleteIds().contains(athleteId)) {
            coach.getAthleteIds().add(athleteId);
            userRepository.save(coach);
        }
    }

    /**
     * Remove an athlete from a coach's roster.
     */
    public void removeAthlete(String coachId, String athleteId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new IllegalArgumentException("Coach not found: " + coachId));
        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        // Remove coach reference from athlete
        if (coachId.equals(athlete.getCoachId())) {
            athlete.setCoachId(null);
            userRepository.save(athlete);
        }

        // Remove from coach's list
        coach.getAthleteIds().remove(athleteId);
        userRepository.save(coach);
    }

    /**
     * Mark a scheduled workout as completed.
     */
    public ScheduledWorkout markCompleted(String scheduledWorkoutId, Integer tss, Double intensityFactor) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.COMPLETED);
        workout.setCompletedAt(LocalDateTime.now());
        if (tss != null)
            workout.setTss(tss);
        if (intensityFactor != null)
            workout.setIntensityFactor(intensityFactor);

        return scheduledWorkoutRepository.save(workout);
    }

    /**
     * Mark a scheduled workout as skipped.
     */
    public ScheduledWorkout markSkipped(String scheduledWorkoutId) {
        ScheduledWorkout workout = scheduledWorkoutRepository.findById(scheduledWorkoutId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled workout not found: " + scheduledWorkoutId));

        workout.setStatus(ScheduleStatus.SKIPPED);
        return scheduledWorkoutRepository.save(workout);
    }

    /**
     * Get athletes filtered by tag for a specific coach.
     */
    @Tool(description = "Get athletes for a coach filtered by a specific tag (e.g. 'Club BTC', 'Junior'). Returns the list of athletes that have the given tag.")
    public List<User> getAthletesByTag(String coachId, String tag) {
        return userRepository.findByCoachIdAndTagsContaining(coachId, tag);
    }

    /**
     * Get all unique tags across a coach's athletes.
     */
    @Tool(description = "Get all unique tags across a coach's athletes. Use this to discover what tags exist before filtering by tag.")
    public List<String> getAthleteTagsForCoach(String coachId) {
        List<User> athletes = userRepository.findByCoachId(coachId);
        return athletes.stream()
                .flatMap(a -> a.getTags().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Add a tag to an athlete.
     */
    public User addTagToAthlete(String coachId, String athleteId, String tag) {
        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        if (!coachId.equals(athlete.getCoachId())) {
            throw new IllegalStateException("Athlete " + athleteId + " is not assigned to coach " + coachId);
        }

        if (!athlete.getTags().contains(tag)) {
            athlete.getTags().add(tag);
            userRepository.save(athlete);
        }
        return athlete;
    }

    /**
     * Remove a tag from an athlete.
     */
    public User removeTagFromAthlete(String coachId, String athleteId, String tag) {
        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        if (!coachId.equals(athlete.getCoachId())) {
            throw new IllegalStateException("Athlete " + athleteId + " is not assigned to coach " + coachId);
        }

        athlete.getTags().remove(tag);
        userRepository.save(athlete);
        return athlete;
    }

    /**
     * Replace all tags for an athlete.
     */
    public User setAthleteTags(String coachId, String athleteId, List<String> tags) {
        User athlete = userRepository.findById(athleteId)
                .orElseThrow(() -> new IllegalArgumentException("Athlete not found: " + athleteId));

        if (!coachId.equals(athlete.getCoachId())) {
            throw new IllegalStateException("Athlete " + athleteId + " is not assigned to coach " + coachId);
        }

        athlete.setTags(new ArrayList<>(tags));
        return userRepository.save(athlete);
    }
}
