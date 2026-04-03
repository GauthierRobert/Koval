package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
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

    public McpRaceTools(RaceService raceService) {
        this.raceService = raceService;
    }

    @Tool(description = "Search the race catalog by title and/or sport. Returns matching races with dates, locations, and distances.")
    public List<RaceSummary> searchRaces(
            @ToolParam(description = "Search query for race title (optional)") String query,
            @ToolParam(description = "Sport filter: CYCLING, RUNNING, SWIMMING, TRIATHLON (optional)") String sport) {
        return raceService.searchRaces(query, sport, null).stream()
                .map(RaceSummary::from)
                .toList();
    }

    @Tool(description = "Create a new race entry in the catalog with full details.")
    public RaceSummary createRace(
            @ToolParam(description = "Race title (e.g. 'Paris Marathon 2026')") String title,
            @ToolParam(description = "Sport: CYCLING, RUNNING, SWIMMING, TRIATHLON, or OTHER") String sport,
            @ToolParam(description = "Scheduled date in YYYY-MM-DD format") String scheduledDate,
            @ToolParam(description = "City or venue name") String location,
            @ToolParam(description = "Country") String country,
            @ToolParam(description = "Display distance (e.g. '42.195 km')") String distance) {
        String userId = SecurityUtils.getCurrentUserId();
        Race race = new Race();
        race.setTitle(title);
        race.setSport(sport);
        race.setScheduledDate(scheduledDate);
        race.setLocation(location);
        race.setCountry(country);
        race.setDistance(distance);
        Race saved = raceService.createRace(userId, race);
        return RaceSummary.from(saved);
    }

    public record RaceSummary(String id, String title, String sport, String scheduledDate,
                               String location, String country) {
        public static RaceSummary from(Race r) {
            return new RaceSummary(
                    r.getId(), r.getTitle(), r.getSport(),
                    r.getScheduledDate(),
                    r.getLocation(), r.getCountry());
        }
    }
}
