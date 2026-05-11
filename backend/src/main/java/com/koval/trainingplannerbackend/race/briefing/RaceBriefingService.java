package com.koval.trainingplannerbackend.race.briefing;

import com.koval.trainingplannerbackend.pacing.gpx.CourseSegment;
import com.koval.trainingplannerbackend.pacing.gpx.GpxParseResult;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.DisciplineCourseSummary;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.KeySegment;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.RaceHeader;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.StartCoordinate;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.WeatherForecast;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.ZoneRow;
import com.koval.trainingplannerbackend.race.briefing.RaceBriefingResponse.ZoneTarget;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.koval.trainingplannerbackend.training.zone.Zone;
import com.koval.trainingplannerbackend.training.zone.ZoneSystem;
import com.koval.trainingplannerbackend.training.zone.ZoneSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds the race-day briefing pack. Aggregates already-existing services
 * (race, GPX parser, zone systems) and the new weather client into one
 * cohesive response the frontend can render on a single printable page.
 *
 * <p>Every section is independent — missing GPX, no zone systems, or weather
 * fetch failure each degrade gracefully (empty list / null), never an error.
 */
@Service
public class RaceBriefingService {

    private static final Logger log = LoggerFactory.getLogger(RaceBriefingService.class);
    private static final int KEY_SEGMENTS_PER_DISCIPLINE = 3;
    /** Minimum elevation gain for a segment to be worth highlighting on the briefing. */
    private static final double KEY_SEGMENT_MIN_GAIN_M = 15.0;

    private final RaceService raceService;
    private final ZoneSystemService zoneSystemService;
    private final WeatherForecastClient weatherClient;

    public RaceBriefingService(RaceService raceService,
                                ZoneSystemService zoneSystemService,
                                WeatherForecastClient weatherClient) {
        this.raceService = raceService;
        this.zoneSystemService = zoneSystemService;
        this.weatherClient = weatherClient;
    }

    public RaceBriefingResponse generate(String raceId, String userId) {
        Race race = raceService.getRaceById(raceId);

        List<DisciplineCourseSummary> courses = buildCourses(race);
        StartCoordinate start = courses.stream()
                .map(DisciplineCourseSummary::start)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        WeatherForecast weather = start == null
                ? null
                : weatherClient.fetchRaceDayForecast(start.lat(), start.lon(), race.getScheduledDate())
                        .orElse(null);

        return new RaceBriefingResponse(
                toHeader(race),
                courses,
                buildZoneTargets(race, userId),
                weather,
                GearChecklists.forRace(race.getSport(), race.getDistanceCategory()),
                LocalDateTime.now()
        );
    }

    private RaceHeader toHeader(Race race) {
        return new RaceHeader(
                race.getId(),
                race.getTitle(),
                race.getSport(),
                race.getDistanceCategory(),
                race.getDistance(),
                race.getLocation(),
                race.getCountry(),
                race.getScheduledDate(),
                race.getWebsite(),
                race.getDescription()
        );
    }

    private List<DisciplineCourseSummary> buildCourses(Race race) {
        List<DisciplineCourseSummary> out = new ArrayList<>(3);
        addCourseIfPresent(out, race, "swim", race.getSwimGpx());
        addCourseIfPresent(out, race, "bike", race.getBikeGpx());
        addCourseIfPresent(out, race, "run", race.getRunGpx());
        return out;
    }

    private void addCourseIfPresent(List<DisciplineCourseSummary> out, Race race, String discipline, byte[] gpx) {
        if (gpx == null || gpx.length == 0) return;
        try {
            GpxParseResult parsed = raceService.parseGpx(race.getId(), discipline);
            out.add(summarize(discipline, parsed));
        } catch (RuntimeException e) {
            log.warn("Skipping {} discipline briefing for race {}: {}", discipline, race.getId(), e.getMessage());
        }
    }

    private DisciplineCourseSummary summarize(String discipline, GpxParseResult parsed) {
        List<CourseSegment> segments = parsed.segments();
        if (segments.isEmpty()) {
            return new DisciplineCourseSummary(discipline, 0.0, 0.0, 0.0, 0.0, 0.0, List.of(), null);
        }

        double totalDistance = segments.getLast().endDistance();
        double gain = 0.0;
        double loss = 0.0;
        double maxGradient = 0.0;
        double weightedGradientSum = 0.0;
        for (CourseSegment s : segments) {
            gain += Math.max(0.0, s.elevationGain());
            loss += Math.max(0.0, s.elevationLoss());
            maxGradient = Math.max(maxGradient, Math.abs(s.averageGradient()));
            weightedGradientSum += s.averageGradient() * s.length();
        }
        double avgGradient = totalDistance > 0 ? weightedGradientSum / totalDistance : 0.0;

        StartCoordinate start = parsed.routeCoordinates().isEmpty()
                ? null
                : new StartCoordinate(
                        parsed.routeCoordinates().getFirst().lat(),
                        parsed.routeCoordinates().getFirst().lon());

        return new DisciplineCourseSummary(
                discipline,
                round1(totalDistance),
                round1(gain),
                round1(loss),
                round1(maxGradient),
                round1(avgGradient),
                topClimbs(segments),
                start
        );
    }

    /**
     * Pick the most notable climbs from the course: highest absolute elevation
     * gain, dropping segments that gain less than {@link #KEY_SEGMENT_MIN_GAIN_M}
     * (noise). Sorted by their position on the course in the final list so the
     * athlete reads them in race order.
     */
    private List<KeySegment> topClimbs(List<CourseSegment> segments) {
        List<CourseSegment> candidates = segments.stream()
                .filter(s -> s.elevationGain() >= KEY_SEGMENT_MIN_GAIN_M)
                .sorted(Comparator.comparingDouble(CourseSegment::elevationGain).reversed())
                .limit(KEY_SEGMENTS_PER_DISCIPLINE)
                .sorted(Comparator.comparingDouble(CourseSegment::startDistance))
                .toList();

        List<KeySegment> out = new ArrayList<>(candidates.size());
        for (CourseSegment s : candidates) {
            out.add(new KeySegment(
                    round1(s.startDistance()),
                    round1(s.endDistance()),
                    round1(s.averageGradient()),
                    round1(s.elevationGain()),
                    labelFor(s)
            ));
        }
        return out;
    }

    private String labelFor(CourseSegment s) {
        double g = s.averageGradient();
        if (g >= 8) return "Steep climb";
        if (g >= 4) return "Climb";
        if (g >= 2) return "Rolling rise";
        if (g <= -8) return "Steep descent";
        if (g <= -4) return "Descent";
        return "Notable section";
    }

    /**
     * Pulls the athlete's default zone systems for the sports involved in the
     * race. For a triathlon we surface zones for all three disciplines; for
     * single-sport races, just one. Missing zone systems are silently skipped.
     */
    private List<ZoneTarget> buildZoneTargets(Race race, String userId) {
        if (userId == null) return List.of();
        List<SportType> sports = sportsFor(race);
        List<ZoneTarget> out = new ArrayList<>(sports.size());
        for (SportType sport : sports) {
            Optional<ZoneSystem> system = zoneSystemService.getDefaultZoneSystem(userId, sport);
            system.map(s -> toZoneTarget(sport, s)).ifPresent(out::add);
        }
        return out;
    }

    private ZoneTarget toZoneTarget(SportType sport, ZoneSystem system) {
        List<ZoneRow> rows = new ArrayList<>();
        if (system.getZones() != null) {
            for (Zone z : system.getZones()) {
                rows.add(new ZoneRow(z.label(), z.low(), z.high(), z.description()));
            }
        }
        return new ZoneTarget(
                sport.name(),
                system.getName(),
                system.getReferenceType() == null ? null : system.getReferenceType().name(),
                system.getReferenceName(),
                system.getReferenceUnit(),
                rows
        );
    }

    private List<SportType> sportsFor(Race race) {
        String sport = race.getSport() == null ? "" : race.getSport().toUpperCase(Locale.ROOT);
        return switch (sport) {
            case "TRIATHLON" -> List.of(SportType.SWIMMING, SportType.CYCLING, SportType.RUNNING);
            case "RUNNING" -> List.of(SportType.RUNNING);
            case "CYCLING" -> List.of(SportType.CYCLING);
            case "SWIMMING" -> List.of(SportType.SWIMMING);
            default -> List.of();
        };
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
