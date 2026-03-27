package com.koval.trainingplannerbackend.training.history;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A recorded workout session with performance metrics, optionally linked to a scheduled workout or FIT file. */
@Getter
@Setter
@Document(collection = "completed_sessions")
public class CompletedSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String trainingId;
    private String title;
    private LocalDateTime completedAt;
    private int totalDurationSeconds;
    private double avgPower;
    private double avgHR;
    private double avgCadence;
    private double avgSpeed; // m/s
    private String sportType;
    private List<BlockSummary> blockSummaries;

    private String scheduledWorkoutId; // Reference to ScheduledWorkout
    private String clubSessionId; // Reference to ClubTrainingSession
    private Double tss;
    private Double intensityFactor;
    private String fitFileId; // GridFS ObjectId; null when no FIT stored
    private Integer rpe;
    private boolean syntheticCompletion; // true when created from planned data via COMPLETE button

    private Integer movingTimeSeconds; // excludes pauses; null if unknown
    private Double totalDistance; // meters
    private Map<Integer, Double> powerCurve; // duration (seconds) -> best avg power (watts)

    @Indexed(unique = true, sparse = true)
    private String stravaActivityId;

    @Indexed(unique = true, sparse = true)
    private String garminActivityId;

    @Indexed(unique = true, sparse = true)
    private String zwiftActivityId;

    public record BlockSummary(
            String label,
            String type,
            int durationSeconds,
            double targetPower,
            double actualPower,
            double actualCadence,
            double actualHR,
            Double distanceMeters) {
    }
}
