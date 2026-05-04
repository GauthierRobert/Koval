package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.goal.RaceGoal;
import com.koval.trainingplannerbackend.goal.RaceGoalService;
import com.koval.trainingplannerbackend.race.DistanceCategory;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool adapter for race catalog operations.
 */
@Service
public class McpRaceTools {

    private final RaceService raceService;
    private final RaceGoalService raceGoalService;

    public McpRaceTools(RaceService raceService, RaceGoalService raceGoalService) {
        this.raceService = raceService;
        this.raceGoalService = raceGoalService;
    }

    @Tool(description = "Search the race catalog by title and/or sport. Returns matching races with dates, locations, and distances.")
    public List<RaceSummary> searchRaces(
            @ToolParam(description = "Search query for race title (optional)") String query,
            @ToolParam(description = "Sport filter: CYCLING, RUNNING, SWIMMING, TRIATHLON (optional)") String sport) {
        return raceService.searchRaces(query, sport, null).stream()
                .map(RaceSummary::from)
                .toList();
    }

    @Tool(description = """
            Create a new race entry in the catalog with full details.

            distanceCategory must be picked from the values allowed for the sport:
              TRIATHLON: TRI_PROMO, TRI_SUPER_SPRINT, TRI_SPRINT, TRI_OLYMPIC, TRI_HALF, TRI_IRONMAN, TRI_ULTRA, TRI_AQUATHLON, TRI_DUATHLON, TRI_AQUABIKE, TRI_CROSS
              RUNNING:   RUN_5K, RUN_10K, RUN_HALF_MARATHON, RUN_MARATHON, RUN_ULTRA
              CYCLING:   BIKE_GRAN_FONDO, BIKE_MEDIO_FONDO, BIKE_TT, BIKE_ULTRA
              SWIMMING:  SWIM_1500M, SWIM_5K, SWIM_10K, SWIM_MARATHON, SWIM_ULTRA
              OTHER:     leave null
            """)
    public RaceSummary createRace(
            @ToolParam(description = "Race title (e.g. 'Paris Marathon 2026')") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, TRIATHLON, or OTHER") String sport,
            @ToolParam(description = "Scheduled date in YYYY-MM-DD format") String scheduledDate,
            @ToolParam(description = "City or venue name") String location,
            @ToolParam(description = "Country") String country,
            @ToolParam(description = "Display distance, metric (e.g. '42.195 km' or '1500m / 40km / 10km')") String distance,
            @ToolParam(required = false, description = "Structured distance category — see tool description for allowed values per sport. Null when no category fits (e.g. OTHER sport).") String distanceCategory,
            @ToolParam(required = false, description = "2-3 paragraphs separated by \\n\\n: overview, course/highlights, optional history/culture") String description,
            @ToolParam(required = false, description = "Official race website URL") String website) {
        String userId = SecurityUtils.getCurrentUserId();
        Race race = new Race();
        race.setTitle(title);
        race.setSport(sport);
        race.setScheduledDate(scheduledDate);
        race.setLocation(location);
        race.setCountry(country);
        race.setDistance(distance);
        race.setDistanceCategory(parseCategory(distanceCategory));
        race.setDescription(description);
        race.setWebsite(website);
        Race saved = raceService.createRace(userId, race);
        return RaceSummary.from(saved);
    }

    private static DistanceCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return DistanceCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Unknown value — RaceService.backfillDistanceCategory will retry from the display string.
            return null;
        }
    }

    @Tool(description = "Get full detail of a single race from the catalog: title, sport, date, location, country, distance, optional discipline distances (swim/bike/run in meters), elevation gain, description and website.")
    public RaceDetail getRace(
            @ToolParam(description = "Race ID") String raceId) {
        return RaceDetail.from(raceService.getRaceById(raceId));
    }

    @Tool(description = "Link a race catalog entry to one of the user's existing race goals. Updates the goal's raceId field and returns the goal id.")
    public String linkRaceToGoal(
            @ToolParam(description = "Race catalog ID") String raceId,
            @ToolParam(description = "Goal ID to link") String goalId) {
        String userId = SecurityUtils.getCurrentUserId();
        RaceGoal updated = raceGoalService.linkToRace(goalId, userId, raceId);
        return "Linked race " + raceId + " to goal " + updated.getId();
    }

    public record RaceSummary(String id, String title, String sport, String scheduledDate,
                               String location, String country, DistanceCategory distanceCategory) {
        public static RaceSummary from(Race r) {
            return new RaceSummary(
                    r.getId(), r.getTitle(), r.getSport(),
                    r.getScheduledDate(),
                    r.getLocation(), r.getCountry(), r.getDistanceCategory());
        }
    }

    public record RaceDetail(String id, String title, String sport, String scheduledDate,
                              String location, String country, String region, String distance,
                              DistanceCategory distanceCategory,
                              Double swimDistanceM, Double bikeDistanceM, Double runDistanceM,
                              Integer elevationGainM, String description, String website) {
        public static RaceDetail from(Race r) {
            return new RaceDetail(
                    r.getId(), r.getTitle(), r.getSport(), r.getScheduledDate(),
                    r.getLocation(), r.getCountry(), r.getRegion(), r.getDistance(),
                    r.getDistanceCategory(),
                    r.getSwimDistanceM(), r.getBikeDistanceM(), r.getRunDistanceM(),
                    r.getElevationGainM(), r.getDescription(), r.getWebsite());
        }
    }
}
