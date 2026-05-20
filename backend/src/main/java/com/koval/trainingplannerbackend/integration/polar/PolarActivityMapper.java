package com.koval.trainingplannerbackend.integration.polar;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Maps a Polar AccessLink exercise summary to a {@link CompletedSession}. */
public class PolarActivityMapper {

    public CompletedSession map(Map<String, Object> exercise) {
        CompletedSession session = new CompletedSession();
        session.setTitle(stringOrDefault(exercise, "detailed-sport-info", "Polar Exercise"));
        session.setSportType(mapSport(exercise));

        Object startTime = exercise.get("start-time");
        if (startTime != null) {
            try {
                session.setCompletedAt(OffsetDateTime.parse(String.valueOf(startTime)).toLocalDateTime());
            } catch (DateTimeParseException e) {
                try {
                    session.setCompletedAt(LocalDateTime.parse(String.valueOf(startTime)));
                } catch (DateTimeParseException ignored) { /* leave null */ }
            }
        }

        // duration is ISO-8601 (e.g. "PT1H30M")
        Object duration = exercise.get("duration");
        if (duration != null) {
            try {
                session.setTotalDurationSeconds((int) Duration.parse(String.valueOf(duration)).toSeconds());
            } catch (DateTimeParseException ignored) { /* leave 0 */ }
        }

        Number distance = (Number) exercise.get("distance");
        if (distance != null) session.setTotalDistance(distance.doubleValue());

        Object hr = exercise.get("heart-rate");
        if (hr instanceof Map<?, ?> hrMap) {
            Object avg = hrMap.get("average");
            if (avg instanceof Number n) session.setAvgHR(n.doubleValue());
        }

        Number calories = (Number) exercise.get("calories");
        // calories isn't on CompletedSession — kept here as a hint we have richer data downstream
        if (calories != null) {
            // no-op; placeholder for future use
        }

        Object id = exercise.get("id");
        if (id != null) session.setPolarActivityId(String.valueOf(id));

        return session;
    }

    private String mapSport(Map<String, Object> exercise) {
        String sport = stringOrDefault(exercise, "sport", "").toUpperCase();
        String detail = stringOrDefault(exercise, "detailed-sport-info", "").toUpperCase();
        String haystack = sport + " " + detail;
        if (haystack.contains("CYCL") || haystack.contains("BIK")) return "CYCLING";
        if (haystack.contains("RUN") || haystack.contains("JOG")) return "RUNNING";
        if (haystack.contains("SWIM") || haystack.contains("POOL")) return "SWIMMING";
        return "OTHER";
    }

    private String stringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
