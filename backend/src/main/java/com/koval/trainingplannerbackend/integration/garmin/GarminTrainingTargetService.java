package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Pushes Koval planned workouts to the athlete's Garmin Connect account as workouts +
 * calendar schedule entries. Garmin's Training API is a two-step push: create the workout,
 * then schedule it on a date. We combine both ids into one external ref formatted as
 * {@code "<workoutId>|<scheduleId>"} so the framework can update / delete cleanly.
 */
@Service
public class GarminTrainingTargetService {

    private static final Logger log = LoggerFactory.getLogger(GarminTrainingTargetService.class);
    private static final String REF_SEPARATOR = "|";

    private final GarminApiClient apiClient;
    private final GarminWorkoutMapper mapper = new GarminWorkoutMapper();

    public GarminTrainingTargetService(GarminApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Push a new workout + schedule to Garmin. Returns the combined external ref. */
    public Optional<String> pushTraining(User athlete, Training training, LocalDate scheduledDate) {
        requireGarminConnected(athlete);
        Map<String, Object> payload = mapper.map(training);

        Optional<String> workoutId = apiClient.createWorkout(
                athlete.getGarminAccessToken(), athlete.getGarminAccessTokenSecret(), payload);
        if (workoutId.isEmpty()) return Optional.empty();

        Optional<String> scheduleId = apiClient.scheduleWorkout(
                athlete.getGarminAccessToken(), athlete.getGarminAccessTokenSecret(),
                workoutId.get(), scheduledDate);

        return Optional.of(workoutId.get() + REF_SEPARATOR + scheduleId.orElse(""));
    }

    /**
     * Re-issue the workout payload and reschedule. Garmin schedule updates are not exposed,
     * so we unschedule + reschedule the existing workout.
     */
    public Optional<String> updateTraining(User athlete, Training training, LocalDate scheduledDate,
                                            String externalRef) {
        requireGarminConnected(athlete);
        Ref ref = Ref.parse(externalRef);
        if (ref == null) return pushTraining(athlete, training, scheduledDate);

        Map<String, Object> payload = mapper.map(training);
        try {
            apiClient.updateWorkout(athlete.getGarminAccessToken(),
                    athlete.getGarminAccessTokenSecret(), ref.workoutId(), payload);
        } catch (RuntimeException e) {
            log.warn("Garmin updateWorkout failed, falling back to create: {}", e.getMessage());
            return pushTraining(athlete, training, scheduledDate);
        }

        if (ref.scheduleId() != null) {
            apiClient.unscheduleWorkout(athlete.getGarminAccessToken(),
                    athlete.getGarminAccessTokenSecret(), ref.scheduleId());
        }
        Optional<String> scheduleId = apiClient.scheduleWorkout(athlete.getGarminAccessToken(),
                athlete.getGarminAccessTokenSecret(), ref.workoutId(), scheduledDate);
        return Optional.of(ref.workoutId() + REF_SEPARATOR + scheduleId.orElse(""));
    }

    /** Delete the schedule entry and the workout. */
    public void deleteTraining(User athlete, String externalRef) {
        if (athlete.getGarminAccessToken() == null) return;
        Ref ref = Ref.parse(externalRef);
        if (ref == null) return;
        if (ref.scheduleId() != null) {
            apiClient.unscheduleWorkout(athlete.getGarminAccessToken(),
                    athlete.getGarminAccessTokenSecret(), ref.scheduleId());
        }
        apiClient.deleteWorkout(athlete.getGarminAccessToken(),
                athlete.getGarminAccessTokenSecret(), ref.workoutId());
    }

    private static void requireGarminConnected(User athlete) {
        if (athlete.getGarminAccessToken() == null || athlete.getGarminAccessTokenSecret() == null) {
            throw new IllegalStateException("Athlete has not connected Garmin");
        }
    }

    private record Ref(String workoutId, String scheduleId) {
        static Ref parse(String externalRef) {
            if (externalRef == null || externalRef.isBlank()) return null;
            String[] parts = externalRef.split("\\" + REF_SEPARATOR, 2);
            String workoutId = parts[0];
            String scheduleId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : null;
            return new Ref(workoutId, scheduleId);
        }
    }
}
