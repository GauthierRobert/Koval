package com.koval.trainingplannerbackend.race;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RaceCompletionService {

    private static final Logger log = LoggerFactory.getLogger(RaceCompletionService.class);
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*\\n(.*?)\\n?```", Pattern.DOTALL);

    private final ChatClient raceCompletionClient;
    private final RaceService raceService;
    private final ObjectReader leniencyReader;

    public RaceCompletionService(@Qualifier("raceCompletionClient") ChatClient raceCompletionClient,
                                  RaceService raceService,
                                  ObjectMapper objectMapper) {
        this.raceCompletionClient = raceCompletionClient;
        this.raceService = raceService;
        // The AI sometimes invents enum values (e.g. for new race formats it doesn't know).
        // Treat unknown DistanceCategory values as null so the merge still applies the
        // valid fields; RaceService.backfillDistanceCategory will retry from the display string.
        this.leniencyReader = objectMapper
                .reader()
                .with(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(Race.class);
    }

    public Race completeRaceDetails(String raceId) {
        Race race = raceService.getRaceById(raceId);

        String response = raceCompletionClient.prompt()
                .user("Race title: \"" + race.getTitle() + "\". Complete the details.")
                .call()
                .content();

        try {
            String json = extractJson(response);
            Race updates = leniencyReader.readValue(json);
            return raceService.updateRace(raceId, updates);
        } catch (Exception e) {
            log.error("Failed to parse AI completion response for race {}", raceId, e);
            return race;
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        Matcher m = CODE_BLOCK.matcher(response.trim());
        return m.find() ? m.group(1).trim() : response.trim();
    }
}
