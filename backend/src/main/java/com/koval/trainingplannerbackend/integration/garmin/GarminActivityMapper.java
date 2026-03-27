package com.koval.trainingplannerbackend.integration.garmin;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Maps Garmin activity data to CompletedSession.
 */
public class GarminActivityMapper {

    public CompletedSession map(Map<String, Object> activity) {
        CompletedSession session = new CompletedSession();

        session.setStravaActivityId(null); // not from Strava
        session.setTitle(stringOrDefault(activity, "activityName", "Garmin Activity"));
        session.setSportType(mapSportType(activity));

        Number startTime = (Number) activity.get("startTimeInSeconds");
        if (startTime != null) {
            session.setCompletedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(startTime.longValue()), ZoneOffset.UTC));
        }

        Number duration = (Number) activity.get("durationInSeconds");
        if (duration != null) {
            session.setTotalDurationSeconds(duration.intValue());
        }

        Number avgHR = (Number) activity.get("averageHeartRateInBeatsPerMinute");
        if (avgHR != null) session.setAvgHR(avgHR.intValue());

        Number avgSpeed = (Number) activity.get("averageSpeedInMetersPerSecond");
        if (avgSpeed != null) session.setAvgSpeed(avgSpeed.doubleValue());

        // Store Garmin activity ID for deduplication
        Object activityId = activity.get("activityId");
        if (activityId != null) {
            session.setGarminActivityId(String.valueOf(activityId));
        }

        return session;
    }

    private String mapSportType(Map<String, Object> activity) {
        String type = stringOrDefault(activity, "activityType", "").toUpperCase();
        if (type.contains("CYCLING") || type.contains("BIKING")) return "CYCLING";
        if (type.contains("RUNNING") || type.contains("TRAIL")) return "RUNNING";
        if (type.contains("SWIMMING") || type.contains("POOL") || type.contains("OPEN_WATER")) return "SWIMMING";
        return "OTHER";
    }

    private String stringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
