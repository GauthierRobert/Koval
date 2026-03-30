package com.koval.trainingplannerbackend.race;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RaceToolService {

    private final RaceService raceService;
    private final RaceCompletionService completionService;
    public RaceToolService(RaceService raceService, RaceCompletionService completionService) {
        this.raceService = raceService;
        this.completionService = completionService;
    }

    @Tool(description = "Search race catalog by title and/or sport.")
    public List<RaceSummary> searchRaces(
            @ToolParam(description = "Title query (optional)") String query,
            @ToolParam(description = "Sport filter (optional)") String sport) {
        return raceService.searchRaces(query, sport, null)
                .stream()
                .map(RaceSummary::from)
                .toList();
    }

    @Tool(description = "Create a race in the catalog (title only, AI completes details).")
    public RaceSummary createRace(
            @ToolParam(description = "Race title") String title,
            ToolContext context) {
        String userId = SecurityUtils.getUserId(context);
        Race race = new Race();
        race.setTitle(title);
        Race savedRace = raceService.createRace(userId, race);
        return RaceSummary.from(completionService.completeRaceDetails(savedRace.getId()));
    }
}
