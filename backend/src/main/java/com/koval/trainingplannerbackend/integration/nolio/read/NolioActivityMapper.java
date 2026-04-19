package com.koval.trainingplannerbackend.integration.nolio.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * Maps Terra's Nolio activity payload to a {@link CompletedSession}.
 *
 * Terra schema reference:
 *   metadata.summary_id         -> nolioActivityId
 *   metadata.start_time         -> completedAt (ISO-8601)
 *   metadata.end_time           -> used for duration fallback
 *   metadata.type               -> sportType (ActivityType enum)
 *   distance_data.summary.distance_meters
 *   power_data.avg_watts / max_watts
 *   heart_rate_data.summary.avg_hr_bpm
 *   movement_data.avg_cadence_rpm / avg_speed_meters_per_second
 *   active_durations_data.activity_seconds (preferred moving time)
 */
@Component
public class NolioActivityMapper {

    // Terra ActivityType enum is numeric 0-148 — group the ones we care about.
    // Exact constants may need refinement once we see real Nolio payloads.
    private static final Set<Integer> CYCLING_TYPES = Set.of(11, 12, 13, 14, 15, 16, 17);
    private static final Set<Integer> RUNNING_TYPES = Set.of(56, 57, 58, 59, 60);
    private static final Set<Integer> SWIMMING_TYPES = Set.of(70, 71, 72);

    public CompletedSession map(JsonNode activity) {
        JsonNode metadata = activity.path("metadata");

        CompletedSession session = new CompletedSession();
        session.setNolioActivityId(text(metadata, "summary_id"));
        session.setTitle(textOr(metadata, "name", "Nolio activity"));
        session.setSyntheticCompletion(false);

        String startTime = text(metadata, "start_time");
        String endTime = text(metadata, "end_time");
        if (startTime != null) {
            try {
                session.setCompletedAt(OffsetDateTime.parse(startTime).toLocalDateTime());
            } catch (DateTimeParseException ignored) {
                // leave null; dedup step handles it defensively
            }
        }

        session.setTotalDurationSeconds(durationSeconds(startTime, endTime));

        int movingSeconds = intPath(activity, "active_durations_data", "activity_seconds");
        if (movingSeconds > 0) {
            session.setMovingTimeSeconds(movingSeconds);
        }

        session.setSportType(mapSportType(metadata.path("type").asInt(-1)));

        session.setAvgPower(doublePath(activity, "power_data", "avg_watts"));
        session.setAvgHR(doublePath(activity, "heart_rate_data", "summary", "avg_hr_bpm"));
        session.setAvgCadence(doublePath(activity, "movement_data", "avg_cadence_rpm"));
        session.setAvgSpeed(doublePath(activity, "movement_data", "avg_speed_meters_per_second"));

        Double distance = doublePathOrNull(activity, "distance_data", "summary", "distance_meters");
        if (distance != null) {
            session.setTotalDistance(distance);
            int blockDuration = movingSeconds > 0 ? movingSeconds : session.getTotalDurationSeconds();
            session.setBlockSummaries(List.of(new CompletedSession.BlockSummary(
                    session.getTitle(),
                    session.getSportType(),
                    blockDuration,
                    0, session.getAvgPower(),
                    session.getAvgCadence(), session.getAvgHR(),
                    distance)));
        }

        return session;
    }

    private static int durationSeconds(String start, String end) {
        if (start == null || end == null) return 0;
        try {
            long seconds = java.time.Duration.between(
                    OffsetDateTime.parse(start), OffsetDateTime.parse(end)).getSeconds();
            return seconds > 0 ? (int) seconds : 0;
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private static String mapSportType(int type) {
        if (CYCLING_TYPES.contains(type)) return "CYCLING";
        if (RUNNING_TYPES.contains(type)) return "RUNNING";
        if (SWIMMING_TYPES.contains(type)) return "SWIMMING";
        return "OTHER";
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String v = text(node, field);
        return v != null ? v : fallback;
    }

    private static double doublePath(JsonNode root, String... path) {
        Double v = doublePathOrNull(root, path);
        return v != null ? v : 0.0;
    }

    private static Double doublePathOrNull(JsonNode root, String... path) {
        JsonNode node = root;
        for (String step : path) {
            node = node.path(step);
        }
        return node.isNumber() ? node.asDouble() : null;
    }

    private static int intPath(JsonNode root, String... path) {
        JsonNode node = root;
        for (String step : path) {
            node = node.path(step);
        }
        return node.isNumber() ? node.asInt() : 0;
    }
}
