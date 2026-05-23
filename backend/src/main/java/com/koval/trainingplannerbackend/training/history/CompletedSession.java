package com.koval.trainingplannerbackend.training.history;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A recorded workout session with performance metrics, optionally linked to a scheduled workout or FIT file. */
@Getter
@Setter
@Document(collection = "completed_sessions")
@CompoundIndexes({
        @CompoundIndex(name = "userId_completedAt_idx", def = "{'userId': 1, 'completedAt': -1}")
})
public class CompletedSession {

    @Id
    private String id;

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

    /**
     * Sub-threshold auto-association candidate: set when scoring landed below the auto-link
     * threshold but above the dismissal floor. The user is prompted in history to confirm or reject.
     * Cleared once the session is firmly linked, dismissed, or marked unplanned.
     */
    private String suggestedScheduledWorkoutId;
    private Integer suggestionScore;

    /** User explicitly declared this session was not part of any planned workout — suppresses prompts. */
    private Boolean unplanned;

    /** Race the athlete classified this session against; set together with {@link #raceRole}. */
    private String raceId;
    /** Role within the race day: RACE (counts toward the race chain) / WARMUP (race-day but separate) / NONE (dismissed). */
    private RaceRole raceRole;

    @Indexed
    private String clubSessionId; // Reference to ClubTrainingSession
    private Double tss;
    /** True when {@link #tss} was derived from RPE (no power/pace signal). Lets us recompute TSS when the athlete changes RPE without clobbering power-based values. */
    private Boolean tssFromRpe;
    private Double intensityFactor;
    private Double normalizedSpeed; // m/s — NGP for running, NSS for swimming, when computed from FIT
    private Double normalizedPower; // watts — NP for cycling, when computed from FIT
    private String fitFileId; // GridFS ObjectId; null when no FIT stored in MongoDB

    /**
     * GCS object name (e.g. {@code fit/{userId}/{sessionId}.fit}) when the FIT
     * blob is also stored in Cloud Storage. Populated by dual-write and the
     * migration backfill; independent of {@link #fitFileId}. Either, both, or
     * neither may be set.
     */
    private String fitGcsObject;
    private Integer rpe;
    private Boolean syntheticCompletion; // true when created from planned data via COMPLETE button
    private Boolean manuallyCreated; // true when added by the athlete via the manual-add UI (no FIT/Strava source)

    /**
     * Identifier of a same-day session bundle ("Brick" / "Race" / warmup chain). Sessions sharing
     * a {@code groupId} are rendered together; the field is opt-in and athlete-controlled —
     * proximity grouping in the UI is purely visual and does not write here.
     */
    @Indexed
    private String groupId;

    private Integer movingTimeSeconds; // excludes pauses; null if unknown
    private Double totalDistance; // meters
    private Map<Integer, Double> powerCurve; // duration (seconds) -> best avg power (watts)

    @Indexed(unique = true, sparse = true)
    private String stravaActivityId;

    @Indexed(unique = true, sparse = true)
    private String garminActivityId;

    @Indexed(unique = true, sparse = true)
    private String zwiftActivityId;

    @Indexed(unique = true, sparse = true)
    private String nolioActivityId;

    @Indexed(unique = true, sparse = true)
    private String polarActivityId;

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
