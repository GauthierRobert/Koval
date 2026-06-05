package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the race domain (RaceController + RaceService):
 * - Create (validation, defaults)
 * - Get / 404
 * - Update: owner-only, merge-if-present semantics
 * - Search: query/sport filters with pagination
 * - Browse + facets aggregation
 */
class RaceFlowIntegrationTest extends BaseIntegrationTest {

    private String athleteToken;
    private String otherToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        athleteToken = loginAthlete("athlete1");
        otherToken = loginAthlete("athlete2");
    }

    @Test
    @DisplayName("Create a race and read it back")
    void createRace_returnsCreatedSummary() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Gran Fondo Mont Ventoux",
                                    "sport": "CYCLING",
                                    "location": "Bedoin",
                                    "country": "France",
                                    "distance": "170 km",
                                    "scheduledDate": "2026-09-12"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Gran Fondo Mont Ventoux"))
                .andExpect(jsonPath("$.sport").value("CYCLING"))
                .andExpect(jsonPath("$.createdBy").value("athlete1"))
                .andExpect(jsonPath("$.verified").value(false))
                .andReturn();

        String raceId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/races/" + raceId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Gran Fondo Mont Ventoux"))
                .andExpect(jsonPath("$.country").value("France"));
    }

    @Test
    @DisplayName("Creating a race without a title is rejected")
    void createRace_givenBlankTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "sport": "CYCLING"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Getting an unknown race returns 404")
    void getRace_givenUnknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/races/does-not-exist")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Only the creator can update a race")
    void updateRace_byNonOwner_returnsForbidden() throws Exception {
        String raceId = createRace(athleteToken, "Protected Race", "RUNNING", "Belgium");

        mockMvc.perform(put("/api/races/" + raceId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Hijacked"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/races/" + raceId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(jsonPath("$.title").value("Protected Race"));
    }

    @Test
    @DisplayName("Update merges only the provided fields")
    void updateRace_mergesOnlyProvidedFields() throws Exception {
        String raceId = createRace(athleteToken, "Merge Race", "CYCLING", "France");

        mockMvc.perform(put("/api/races/" + raceId)
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location": "Nice", "website": "https://example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Merge Race"))
                .andExpect(jsonPath("$.sport").value("CYCLING"))
                .andExpect(jsonPath("$.country").value("France"))
                .andExpect(jsonPath("$.location").value("Nice"))
                .andExpect(jsonPath("$.website").value("https://example.com"));
    }

    @Test
    @DisplayName("Search filters by title query and sport with pagination")
    void searchRaces_filtersByQueryAndSport() throws Exception {
        createRace(athleteToken, "Ironman Nice", "TRIATHLON", "France");
        createRace(athleteToken, "Ironman Hawaii", "TRIATHLON", "USA");
        createRace(athleteToken, "Nice Marathon", "RUNNING", "France");

        // Query + sport
        mockMvc.perform(get("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .param("q", "ironman")
                        .param("sport", "TRIATHLON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Query only — matches across sports
        mockMvc.perform(get("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .param("q", "nice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Sport only
        mockMvc.perform(get("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .param("sport", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Nice Marathon"));

        // Pagination
        mockMvc.perform(get("/api/races")
                        .header("Authorization", bearer(athleteToken))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    @DisplayName("Browse filters by sport and country")
    void browseRaces_filtersBySportAndCountry() throws Exception {
        createRace(athleteToken, "Tour of Flanders Sportive", "CYCLING", "Belgium");
        createRace(athleteToken, "Paris-Roubaix Challenge", "CYCLING", "France");
        createRace(athleteToken, "Brussels Marathon", "RUNNING", "Belgium");

        mockMvc.perform(get("/api/races/browse")
                        .header("Authorization", bearer(athleteToken))
                        .param("sport", "CYCLING")
                        .param("country", "Belgium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Tour of Flanders Sportive"));
    }

    @Test
    @DisplayName("Sport and country facets aggregate created races")
    void facets_aggregateBySportAndCountry() throws Exception {
        createRace(athleteToken, "Race A", "CYCLING", "France");
        createRace(athleteToken, "Race B", "CYCLING", "Belgium");
        createRace(athleteToken, "Race C", "RUNNING", "France");

        mockMvc.perform(get("/api/races/facets/sports")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sport").value("CYCLING"))
                .andExpect(jsonPath("$[0].raceCount").value(2))
                .andExpect(jsonPath("$[0].countryCount").value(2));

        mockMvc.perform(get("/api/races/facets/countries")
                        .header("Authorization", bearer(athleteToken))
                        .param("sport", "CYCLING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // ---------- helpers ----------

    private String createRace(String token, String title, String sport, String country) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/races")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "%s", "sport": "%s", "country": "%s"}
                                """.formatted(title, sport, country)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
