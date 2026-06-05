package com.koval.trainingplannerbackend.integration.suunto;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * Maps a Suunto Cloud API workout summary to a {@link CompletedSession}.
 *
 * <p>Only summary-level fields are mapped here; per-second power/HR/GPS detail comes from the
 * FIT file stored alongside the session, which feeds the normal recompute path
 * (power curve, NP, normalized speed).
 */
public class SuuntoActivityMapper {

    // Suunto activity ids (see the "Suunto Watches - FIT Activities" doc in the API zone).
    private static final Set<Integer> CYCLING_IDS = Set.of(3);
    private static final Set<Integer> RUNNING_IDS = Set.of(2, 22);
    private static final Set<Integer> SWIMMING_IDS = Set.of(21);

    public CompletedSession map(Map<String, Object> workout) {
        CompletedSession session = new CompletedSession();

        Object workoutKey = workout.get("workoutKey");
        if (workoutKey != null) session.setSuuntoActivityId(String.valueOf(workoutKey));

        session.setTitle(title(workout));
        session.setSportType(mapSport(workout));

        Number startTime = (Number) workout.get("startTime"); // epoch millis
        if (startTime != null) {
            session.setCompletedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(startTime.longValue()), ZoneId.systemDefault()));
        }

        Number totalTime = (Number) workout.get("totalTime"); // seconds
        if (totalTime != null) session.setTotalDurationSeconds((int) Math.round(totalTime.doubleValue()));

        Number distance = (Number) workout.get("totalDistance"); // meters
        if (distance != null) session.setTotalDistance(distance.doubleValue());

        Number avgHr = (Number) workout.get("avgHR");
        if (avgHr != null) session.setAvgHR(avgHr.doubleValue());

        Number avgSpeed = (Number) workout.get("avgSpeed"); // m/s
        if (avgSpeed != null) session.setAvgSpeed(avgSpeed.doubleValue());

        return session;
    }

    private String title(Map<String, Object> workout) {
        Object name = workout.get("workoutName");
        if (name == null) name = workout.get("description");
        String title = name != null ? String.valueOf(name).trim() : "";
        return title.isEmpty() ? "Suunto Workout" : title;
    }

    private String mapSport(Map<String, Object> workout) {
        Object activityId = workout.get("activityId");
        if (activityId instanceof Number n) {
            int id = n.intValue();
            if (CYCLING_IDS.contains(id)) return "CYCLING";
            if (RUNNING_IDS.contains(id)) return "RUNNING";
            if (SWIMMING_IDS.contains(id)) return "SWIMMING";
        }
        // Fallback on textual hints when the numeric id is missing or unknown.
        String haystack = (workout.get("activityName") + " " + workout.get("workoutName")).toUpperCase();
        if (haystack.contains("CYCL") || haystack.contains("BIK")) return "CYCLING";
        if (haystack.contains("RUN") || haystack.contains("JOG")) return "RUNNING";
        if (haystack.contains("SWIM") || haystack.contains("POOL")) return "SWIMMING";
        return "OTHER";
    }
}
