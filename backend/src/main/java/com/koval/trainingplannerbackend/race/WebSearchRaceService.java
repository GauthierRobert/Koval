package com.koval.trainingplannerbackend.race;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses the Anthropic Messages API with the web_search tool to look up
 * real-time race information (dates, distances, location, etc.) and return
 * a Race-compatible JSON structure.
 * <p>
 * This does NOT use Spring AI because web search is a server-side tool
 * not supported by the Spring AI Anthropic integration.
 */
@Service
public class WebSearchRaceService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchRaceService.class);

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 2048;
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);

    private static final String SYSTEM_PROMPT = """
            You are a race research assistant with web search capabilities.
            When asked about a race, search the web for accurate, up-to-date information.

            Return ONLY a valid JSON object with these fields:
            - title (String): Official race name
            - sport (String): CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER
            - location (String): City name
            - country (String): Country name
            - region (String): State/province/region
            - distance (String): Display string in metric units. For triathlon use
              "<swim>m / <bike>km / <run>km" (e.g. "1500m / 40km / 10km"). For
              single-discipline races use "<distance> km" or "<distance> m".
            - distanceCategory (String): structured enum value picked from the allowed
              list for the sport (omit field if no category clearly fits):
                TRIATHLON: TRI_PROMO, TRI_SUPER_SPRINT, TRI_SPRINT, TRI_OLYMPIC, TRI_HALF, TRI_IRONMAN, TRI_ULTRA, TRI_AQUATHLON, TRI_DUATHLON, TRI_AQUABIKE, TRI_CROSS
                RUNNING:   RUN_5K, RUN_10K, RUN_HALF_MARATHON, RUN_MARATHON, RUN_ULTRA
                CYCLING:   BIKE_GRAN_FONDO, BIKE_MEDIO_FONDO, BIKE_TT, BIKE_ULTRA
                SWIMMING:  SWIM_1500M, SWIM_5K, SWIM_10K, SWIM_MARATHON, SWIM_ULTRA
            - swimDistanceM (Double): Swim distance in meters, null if not applicable
            - bikeDistanceM (Double): Bike distance in meters, null if not applicable
            - runDistanceM (Double): Run distance in meters, null if not applicable
            - elevationGainM (Integer): Total elevation gain in meters, null if unknown
            - description (String): 5-10 sentence description of the race, correctly structured/formatted.
            - website (String): Official website URL, null if unknown
            - scheduledDate (String): Next upcoming edition date in YYYY-MM-DD format, null if unknown

            After searching, return ONLY the JSON. No markdown fences, no explanation.
            If you cannot find reliable information for a field, set it to null.
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectReader raceReader;

    public WebSearchRaceService(@Value("${spring.ai.anthropic.api-key}") String apiKey,
                                ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        // Tolerate unknown enum values (AI may invent categories) and unknown JSON
        // properties — invalid distanceCategory becomes null and is later inferred
        // from the display string by RaceService.
        this.raceReader = objectMapper
                .reader()
                .with(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(Race.class);
        this.restClient = RestClient.builder()
                .baseUrl(ANTHROPIC_API_URL)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }

    /**
     * Searches the web for race information and returns a Race object
     * with fields populated from search results.
     *
     * @param query search query, typically a race name (e.g. "Ironman Nice 2026")
     * @return Race with fields populated from web search, or a minimal Race if search fails
     */
    public Race searchRaceDetails(String query) {
        try {
            AnthropicResponse response = callAnthropicWithWebSearch(query);
            String textContent = extractTextContent(response);
            String json = extractJson(textContent);
            Race race = raceReader.readValue(json);
            if (race.getDistanceCategory() == null && race.getSport() != null && race.getDistance() != null) {
                race.setDistanceCategory(DistanceCategory.infer(race.getSport(), race.getDistance()));
            }
            return race;
        } catch (Exception e) {
            log.error("Web search failed for race query '{}': {}", query, e.getMessage());
            Race fallback = new Race();
            fallback.setTitle(query);
            return fallback;
        }
    }

    private AnthropicResponse callAnthropicWithWebSearch(String query) {
        Map<String, Object> request = buildRequest(query);

        AnthropicResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AnthropicResponse.class);

        // Handle pause_turn: the API may pause when web search results are ready
        // and need to be sent back for continued processing
        if (response != null && "pause_turn".equals(response.stopReason)) {
            return continueAfterPause(response);
        }

        return response;
    }

    private Map<String, Object> buildRequest(String query) {
        return Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "system", SYSTEM_PROMPT,
                "tools", List.of(
                        Map.of(
                                "type", "web_search_20250305",
                                "name", "web_search",
                                "max_uses", 5
                        )
                ),
                "messages", List.of(
                        Map.of("role", "user", "content",
                                "Search the web for details about this race: \"" + query + "\". "
                                + "Find the official website, next scheduled date, distances, location, and description. "
                                + "Return the information as JSON.")
                )
        );
    }

    /**
     * When the API returns pause_turn, we need to send the response content back
     * as an assistant message and add an empty user message to continue.
     */
    private AnthropicResponse continueAfterPause(AnthropicResponse pausedResponse) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content",
                "Search the web for details about this race. "
                + "Find the official website, next scheduled date, distances, location, and description. "
                + "Return the information as JSON."));
        messages.add(Map.of("role", "assistant", "content", pausedResponse.content));
        messages.add(Map.of("role", "user", "content", "Continue."));

        Map<String, Object> request = Map.of(
                "model", MODEL,
                "max_tokens", MAX_TOKENS,
                "system", SYSTEM_PROMPT,
                "tools", List.of(
                        Map.of(
                                "type", "web_search_20250305",
                                "name", "web_search",
                                "max_uses", 5
                        )
                ),
                "messages", messages
        );

        AnthropicResponse continued = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AnthropicResponse.class);

        if (continued != null && "pause_turn".equals(continued.stopReason)) {
            // Merge content and recurse (max depth limited by max_uses)
            List<Object> merged = new ArrayList<>(pausedResponse.content);
            merged.addAll(continued.content);
            continued.content = merged;
            return continueAfterPause(continued);
        }

        return continued;
    }

    private String extractTextContent(AnthropicResponse response) {
        if (response == null || response.content == null) return "{}";

        StringBuilder sb = new StringBuilder();
        for (Object block : response.content) {
            if (block instanceof Map<?, ?> map) {
                if ("text".equals(map.get("type"))) {
                    Object text = map.get("text");
                    if (text != null) sb.append(text);
                }
            }
        }
        return sb.isEmpty() ? "{}" : sb.toString();
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        Matcher m = CODE_BLOCK.matcher(text.trim());
        if (m.find()) return m.group(1).trim();
        // Try to find raw JSON object
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    // ── Anthropic API response DTOs ──────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnthropicResponse {
        @JsonProperty("id")
        public String id;

        @JsonProperty("content")
        public List<Object> content;

        @JsonProperty("stop_reason")
        public String stopReason;

        @JsonProperty("usage")
        public Map<String, Object> usage;
    }
}
