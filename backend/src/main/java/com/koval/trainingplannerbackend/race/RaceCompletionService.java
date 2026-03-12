package com.koval.trainingplannerbackend.race;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class RaceCompletionService {

    private static final Logger log = LoggerFactory.getLogger(RaceCompletionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient raceCompletionClient;
    private final RaceService raceService;

    public RaceCompletionService(@Qualifier("raceCompletionClient") ChatClient raceCompletionClient,
                                  RaceService raceService) {
        this.raceCompletionClient = raceCompletionClient;
        this.raceService = raceService;
    }

    public Race completeRaceDetails(String raceId) {
        Race race = raceService.getRaceById(raceId);

        String response = raceCompletionClient.prompt()
                .user("Race title: \"" + race.getTitle() + "\". Complete the details.")
                .call()
                .content();

        try {
            // Extract JSON from response (may be wrapped in markdown code block)
            String json = extractJson(response);
            JsonNode node = MAPPER.readTree(json);

            Race updates = new Race();
            if (node.has("sport")) updates.setSport(node.get("sport").asText());
            if (node.has("location")) updates.setLocation(node.get("location").asText());
            if (node.has("country")) updates.setCountry(node.get("country").asText());
            if (node.has("region")) updates.setRegion(node.get("region").asText());
            if (node.has("distance")) updates.setDistance(node.get("distance").asText());
            if (node.has("swimDistanceM") && !node.get("swimDistanceM").isNull())
                updates.setSwimDistanceM(node.get("swimDistanceM").asDouble());
            if (node.has("bikeDistanceM") && !node.get("bikeDistanceM").isNull())
                updates.setBikeDistanceM(node.get("bikeDistanceM").asDouble());
            if (node.has("runDistanceM") && !node.get("runDistanceM").isNull())
                updates.setRunDistanceM(node.get("runDistanceM").asDouble());
            if (node.has("elevationGainM") && !node.get("elevationGainM").isNull())
                updates.setElevationGainM(node.get("elevationGainM").asInt());
            if (node.has("description")) updates.setDescription(node.get("description").asText());
            if (node.has("website")) updates.setWebsite(node.get("website").asText());
            if (node.has("typicalMonth") && !node.get("typicalMonth").isNull())
                updates.setTypicalMonth(node.get("typicalMonth").asInt());

            return raceService.updateRace(raceId, updates);
        } catch (Exception e) {
            log.warn("Failed to parse AI completion response for race {}: {}", raceId, e.getMessage());
            return race;
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        // Strip markdown code fences
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
