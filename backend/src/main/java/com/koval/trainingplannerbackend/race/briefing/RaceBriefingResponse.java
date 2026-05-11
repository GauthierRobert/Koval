package com.koval.trainingplannerbackend.race.briefing;

import com.koval.trainingplannerbackend.race.DistanceCategory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Race-day briefing pack. Aggregates everything an athlete needs the night
 * before a race onto one printable page: course profile per discipline,
 * target intensity zones, weather forecast at the start line, and a gear
 * checklist tailored to the discipline mix.
 *
 * <p>Designed to be rendered as a print-friendly HTML page (browser "Save as
 * PDF" produces the takeaway artifact). No server-side PDF library required.
 */
public record RaceBriefingResponse(
        RaceHeader race,
        List<DisciplineCourseSummary> courses,
        List<ZoneTarget> targetZones,
        WeatherForecast weather,
        GearChecklist gear,
        LocalDateTime generatedAt
) {

    public record RaceHeader(
            String id,
            String title,
            String sport,
            DistanceCategory category,
            String distance,
            String location,
            String country,
            String scheduledDate,
            String website,
            String description
    ) {}

    /**
     * Per-discipline summary of the course: aggregated distance/elevation plus
     * the steepest segments (for the "watch out at km X" line on the briefing).
     */
    public record DisciplineCourseSummary(
            String discipline,
            Double distanceM,
            Double elevationGainM,
            Double elevationLossM,
            Double maxGradientPercent,
            Double avgGradientPercent,
            List<KeySegment> keySegments,
            StartCoordinate start
    ) {}

    public record KeySegment(
            double startDistanceM,
            double endDistanceM,
            double gradientPercent,
            double elevationGainM,
            String label
    ) {}

    public record StartCoordinate(double lat, double lon) {}

    public record ZoneTarget(
            String sportType,
            String systemName,
            String referenceType,
            String referenceName,
            String referenceUnit,
            List<ZoneRow> zones
    ) {}

    public record ZoneRow(
            String label,
            Integer low,
            Integer high,
            String description
    ) {}

    /**
     * Hourly forecast around the race window. Open-Meteo returns local time
     * already; the timezone field carries the IANA zone for display.
     */
    public record WeatherForecast(
            double latitude,
            double longitude,
            String timezone,
            String source,
            List<HourlyForecast> hourly,
            String warning
    ) {}

    public record HourlyForecast(
            String time,
            Double temperatureC,
            Double precipitationMm,
            Double windSpeedKmh,
            Integer weatherCode
    ) {}

    public record GearChecklist(List<GearGroup> groups) {}

    public record GearGroup(String name, List<String> items) {}
}
