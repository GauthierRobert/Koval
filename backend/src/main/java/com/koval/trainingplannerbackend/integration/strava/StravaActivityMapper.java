package com.koval.trainingplannerbackend.integration.strava;

import com.koval.trainingplannerbackend.training.history.CompletedSession;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StravaActivityMapper {

    private static final Set<String> CYCLING_TYPES = Set.of("Ride", "VirtualRide", "EBikeRide");
    private static final Set<String> RUNNING_TYPES = Set.of("Run", "VirtualRun", "TrailRun");
    private static final Set<String> SWIMMING_TYPES = Set.of("Swim");

    public CompletedSession map(Map<String, Object> activity) {
        CompletedSession session = new CompletedSession();

        session.setStravaActivityId(String.valueOf(activity.get("id")));
        session.setTitle((String) activity.get("name"));
        session.setSyntheticCompletion(false);

        // Parse ISO 8601 start_date
        String startDate = (String) activity.get("start_date");
        if (startDate != null) {
            session.setCompletedAt(OffsetDateTime.parse(startDate).toLocalDateTime());
        }

        session.setTotalDurationSeconds(intValue(activity.get("elapsed_time")));

        // Power: only use if device_watts is true
        boolean deviceWatts = Boolean.TRUE.equals(activity.get("device_watts"));
        session.setAvgPower(deviceWatts ? doubleValue(activity.get("average_watts")) : 0);

        session.setAvgHR(doubleValue(activity.get("average_heartrate")));
        session.setAvgCadence(doubleValue(activity.get("average_cadence")));
        session.setAvgSpeed(doubleValue(activity.get("average_speed")));

        // Sport type mapping
        String type = (String) activity.get("type");
        session.setSportType(mapSportType(type));

        // Fallback single block summary (overridden if laps are fetched)
        Double distance = doubleValueOrNull(activity.get("distance"));
        if (distance != null) {
            CompletedSession.BlockSummary summary = new CompletedSession.BlockSummary(
                    session.getTitle(),
                    session.getSportType(),
                    session.getTotalDurationSeconds(),
                    0, session.getAvgPower(),
                    session.getAvgCadence(), session.getAvgHR(),
                    distance);
            session.setBlockSummaries(List.of(summary));
        }

        return session;
    }

    /**
     * Convert Strava laps to per-lap BlockSummary list.
     * Returns null if laps has 1 or fewer entries (no useful breakdown).
     */
    public List<CompletedSession.BlockSummary> mapLaps(List<Map<String, Object>> laps, String sportType, boolean deviceWatts) {
        if (laps == null || laps.size() <= 1) return null;

        return laps.stream().map(lap -> {
            String name = (String) lap.get("name");
            int elapsed = intValue(lap.get("elapsed_time"));
            double power = deviceWatts ? doubleValue(lap.get("average_watts")) : 0;
            double hr = doubleValue(lap.get("average_heartrate"));
            double cadence = doubleValue(lap.get("average_cadence"));
            Double distance = doubleValueOrNull(lap.get("distance"));

            return new CompletedSession.BlockSummary(
                    name != null ? name : "Lap",
                    sportType,
                    elapsed,
                    0, power, cadence, hr, distance);
        }).toList();
    }

    private String mapSportType(String stravaType) {
        if (stravaType == null) return "OTHER";
        if (CYCLING_TYPES.contains(stravaType)) return "CYCLING";
        if (RUNNING_TYPES.contains(stravaType)) return "RUNNING";
        if (SWIMMING_TYPES.contains(stravaType)) return "SWIMMING";
        return "OTHER";
    }

    private static int intValue(Object obj) {
        return obj instanceof Number n ? n.intValue() : 0;
    }

    private static double doubleValue(Object obj) {
        return obj instanceof Number n ? n.doubleValue() : 0;
    }

    private static Double doubleValueOrNull(Object obj) {
        return obj instanceof Number n ? n.doubleValue() : null;
    }
}
