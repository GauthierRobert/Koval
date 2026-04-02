package com.koval.trainingplannerbackend.mcp;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceCompletionService;
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
    private final RaceCompletionService completionService;

    public McpRaceTools(RaceService raceService, RaceCompletionService completionService) {
        this.raceService = raceService;
        this.completionService = completionService;
    }

    @Tool(description = "Search the race catalog by title and/or sport. Returns matching races with dates, locations, and distances.")
    public List<RaceSummary> searchRaces(
            @ToolParam(description = "Search query for race title (optional)") String query,
            @ToolParam(description = "Sport filter: CYCLING, RUNNING, SWIMMING, TRIATHLON (optional)") String sport) {
        return raceService.searchRaces(query, sport, null).stream()
                .map(RaceSummary::from)
                .toList();
    }

    @Tool(description = "Create a new race entry in the catalog. AI will auto-complete race details (date, location, distances) from the title.")
    public RaceSummary createRace(
            @ToolParam(description = "Race title (e.g. 'Paris Marathon 2026')") String title) {
        String userId = SecurityUtils.getCurrentUserId();
        Race race = new Race();
        race.setTitle(title);
        Race saved = raceService.createRace(userId, race);
        return RaceSummary.from(completionService.completeRaceDetails(saved.getId()));
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
