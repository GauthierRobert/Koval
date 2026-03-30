package com.koval.trainingplannerbackend.integration.zwift;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Maps Zwift activity data to CompletedSession.
 * Zwift activities are always CYCLING.
 */
public class ZwiftActivityMapper {

    public CompletedSession map(Map<String, Object> activity) {
        CompletedSession session = new CompletedSession();

        session.setSportType("CYCLING");
        session.setTitle(stringOrDefault(activity, "name", "Zwift Ride"));

        // Zwift uses ISO 8601 dates
        String startDate = (String) activity.get("startDate");
        if (startDate != null) {
            try {
                session.setCompletedAt(LocalDateTime.ofInstant(Instant.parse(startDate), ZoneOffset.UTC));
            } catch (DateTimeException ignored) {}
        }

        Number duration = (Number) activity.get("movingTimeInMs");
        if (duration != null) {
            session.setTotalDurationSeconds((int) (duration.longValue() / 1000));
        }

        Number avgWatts = (Number) activity.get("avgWatts");
        if (avgWatts != null) session.setAvgPower(avgWatts.intValue());

        Number avgHR = (Number) activity.get("avgHeartRate");
        if (avgHR != null) session.setAvgHR(avgHR.intValue());

        Number avgCadence = (Number) activity.get("avgCadence");
        if (avgCadence != null) session.setAvgCadence(avgCadence.intValue());

        Number distance = (Number) activity.get("distanceInMeters");
        if (distance != null) {
            double distM = distance.doubleValue();
            Number durationSec = (Number) activity.get("movingTimeInMs");
            if (durationSec != null && durationSec.longValue() > 0) {
                session.setAvgSpeed(distM / (durationSec.longValue() / 1000.0));
            }
        }

        Object activityId = activity.get("id");
        if (activityId != null) {
            session.setZwiftActivityId(String.valueOf(activityId));
        }

        return session;
    }

    private String stringOrDefault(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
