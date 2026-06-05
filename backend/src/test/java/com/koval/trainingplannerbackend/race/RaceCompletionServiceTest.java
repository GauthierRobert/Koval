package com.koval.trainingplannerbackend.race;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RaceCompletionService's lenient handling of AI responses:
 * - JSON extraction from markdown code fences
 * - Unknown enum values tolerated (READ_UNKNOWN_ENUM_VALUES_AS_NULL)
 * - Unparseable responses fall back to the unmodified race
 */
class RaceCompletionServiceTest {

    private ChatClient chatClient;
    private RaceService raceService;
    private RaceCompletionService service;
    private Race existing;

    @BeforeEach
    void setup() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        raceService = mock(RaceService.class);
        service = new RaceCompletionService(chatClient, raceService, new ObjectMapper());

        existing = new Race();
        existing.setId("race-1");
        existing.setTitle("Ironman Nice");
        when(raceService.getRaceById("race-1")).thenReturn(existing);
        when(raceService.updateRace(eq("race-1"), any(Race.class))).thenAnswer(inv -> inv.getArgument(1));
    }

    private void aiResponds(String content) {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(content);
    }

    @Test
    @DisplayName("Parses JSON wrapped in a markdown code fence and applies the update")
    void completeRaceDetails_givenFencedJson_appliesUpdate() {
        aiResponds("""
                Here are the details:
                ```json
                {"sport": "TRIATHLON", "country": "France", "distanceCategory": "TRI_IRONMAN"}
                ```
                """);

        service.completeRaceDetails("race-1");

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceService).updateRace(eq("race-1"), captor.capture());
        assertThat(captor.getValue().getSport()).isEqualTo("TRIATHLON");
        assertThat(captor.getValue().getCountry()).isEqualTo("France");
        assertThat(captor.getValue().getDistanceCategory()).isEqualTo(DistanceCategory.TRI_IRONMAN);
    }

    @Test
    @DisplayName("Parses raw JSON without a code fence")
    void completeRaceDetails_givenRawJson_appliesUpdate() {
        aiResponds("""
                {"sport": "CYCLING", "location": "Bedoin"}
                """);

        service.completeRaceDetails("race-1");

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceService).updateRace(eq("race-1"), captor.capture());
        assertThat(captor.getValue().getSport()).isEqualTo("CYCLING");
        assertThat(captor.getValue().getLocation()).isEqualTo("Bedoin");
    }

    @Test
    @DisplayName("Unknown DistanceCategory values are tolerated as null; valid fields still apply")
    void completeRaceDetails_givenUnknownEnumValue_appliesRemainingFields() {
        aiResponds("""
                ```json
                {"sport": "TRIATHLON", "distanceCategory": "MADE_UP_FORMAT", "country": "France"}
                ```
                """);

        service.completeRaceDetails("race-1");

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceService).updateRace(eq("race-1"), captor.capture());
        assertThat(captor.getValue().getDistanceCategory()).isNull();
        assertThat(captor.getValue().getSport()).isEqualTo("TRIATHLON");
        assertThat(captor.getValue().getCountry()).isEqualTo("France");
    }

    @Test
    @DisplayName("Unknown JSON properties are ignored instead of failing")
    void completeRaceDetails_givenUnknownProperties_ignoresThem() {
        aiResponds("""
                ```json
                {"sport": "RUNNING", "inventedField": "whatever"}
                ```
                """);

        service.completeRaceDetails("race-1");

        ArgumentCaptor<Race> captor = ArgumentCaptor.forClass(Race.class);
        verify(raceService).updateRace(eq("race-1"), captor.capture());
        assertThat(captor.getValue().getSport()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("Unparseable response returns the original race without updating")
    void completeRaceDetails_givenGarbageResponse_returnsOriginalRace() {
        aiResponds("Sorry, I could not find any information about this race.");

        Race result = service.completeRaceDetails("race-1");

        assertThat(result).isSameAs(existing);
        verify(raceService, never()).updateRace(anyString(), any(Race.class));
    }

    @Test
    @DisplayName("Null AI response returns the race after a no-op merge")
    void completeRaceDetails_givenNullResponse_doesNotFail() {
        aiResponds(null);

        Race result = service.completeRaceDetails("race-1");

        // extractJson maps null to "{}", which parses as an empty update.
        verify(raceService).updateRace(eq("race-1"), any(Race.class));
        assertThat(result).isNotNull();
    }
}
