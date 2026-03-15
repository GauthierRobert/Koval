package com.koval.trainingplannerbackend.race;

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

    @Tool(description = "Search the global race catalog by title and/or sport. Returns matching races with their details.")
    public List<RaceSummary> searchRaces(
            @ToolParam(description = "Search query for race title (optional)") String query,
            @ToolParam(description = "Sport filter: CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER (optional)") String sport) {
        return raceService.searchRaces(query, sport, null)
                .stream()
                .map(RaceSummary::from)
                .toList();
    }

    @Tool(description = "Create a new race in the global catalog. Only title is required — use AI completion to fill the rest.")
    public RaceSummary createRace(
            @ToolParam(description = "User ID") String userId,
            @ToolParam(description = "Race title (e.g. 'Ironman Nice', 'Paris-Roubaix')") String title) {
        Race race = new Race();
        race.setTitle(title);
        Race savedRace = raceService.createRace(userId, race);
        return RaceSummary.from(completionService.completeRaceDetails(savedRace.getId()));
    }

    public record RaceSummary(
            String id, String title, String sport,
            String location, String country, String distance,
            Double swimDistanceM, Double bikeDistanceM, Double runDistanceM,
            Integer elevationGainM, String website
    ) {
        static RaceSummary from(Race r) {
            return new RaceSummary(
                    r.getId(), r.getTitle(), r.getSport(),
                    r.getLocation(), r.getCountry(), r.getDistance(),
                    r.getSwimDistanceM(), r.getBikeDistanceM(), r.getRunDistanceM(),
                    r.getElevationGainM(), r.getWebsite()
            );
        }
    }
}
