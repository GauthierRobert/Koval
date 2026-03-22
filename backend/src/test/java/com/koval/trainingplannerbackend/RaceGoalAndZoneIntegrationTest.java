package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for race goals and zone system CRUD.
 */
class RaceGoalAndZoneIntegrationTest extends BaseIntegrationTest {

    private String athleteToken;
    private String coachToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        athleteToken = loginAthlete("athlete1");
        coachToken = loginCoach("coach1");
    }

    // --- Race Goals ---

    @Test
    @DisplayName("CRUD race goals: create, list, update, delete")
    void raceGoalCrud() throws Exception {
        // Create
        MvcResult result = mockMvc.perform(post("/api/goals")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Ironman Nice",
                                    "sport": "TRIATHLON",
                                    "raceDate": "%s",
                                    "priority": "A",
                                    "distance": "140.6 miles",
                                    "location": "Nice, France",
                                    "targetTime": "10:30:00",
                                    "notes": "Main goal race"
                                }
                                """.formatted(LocalDate.now().plusMonths(6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ironman Nice"))
                .andExpect(jsonPath("$.priority").value("A"))
                .andReturn();

        String goalId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        // List
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Ironman Nice"));

        // Update
        mockMvc.perform(put("/api/goals/" + goalId)
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Ironman Nice 2026",
                                    "sport": "TRIATHLON",
                                    "raceDate": "%s",
                                    "priority": "A",
                                    "targetTime": "10:00:00"
                                }
                                """.formatted(LocalDate.now().plusMonths(6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Ironman Nice 2026"))
                .andExpect(jsonPath("$.targetTime").value("10:00:00"));

        // Delete
        mockMvc.perform(delete("/api/goals/" + goalId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Race goals are user-isolated")
    void raceGoalsUserIsolation() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "My Race", "sport": "CYCLING", "raceDate": "%s", "priority": "B"}
                                """.formatted(LocalDate.now().plusMonths(3))))
                .andExpect(status().isCreated());

        String athlete2Token = loginAthlete("athlete2");
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athlete2Token)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- Zone System ---

    @Test
    @DisplayName("CRUD zone systems: create, list, get, update, delete")
    void zoneSystemCrud() throws Exception {
        // Create
        MvcResult result = mockMvc.perform(post("/api/zones/coach")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Coggan Power Zones",
                                    "sportType": "CYCLING",
                                    "referenceType": "FTP",
                                    "referenceName": "FTP",
                                    "zones": [
                                        {"name": "Z1 Recovery", "low": 0, "high": 55, "color": "#90EE90"},
                                        {"name": "Z2 Endurance", "low": 56, "high": 75, "color": "#87CEEB"},
                                        {"name": "Z3 Tempo", "low": 76, "high": 90, "color": "#FFD700"},
                                        {"name": "Z4 Threshold", "low": 91, "high": 105, "color": "#FFA500"},
                                        {"name": "Z5 VO2max", "low": 106, "high": 120, "color": "#FF4500"},
                                        {"name": "Z6 Anaerobic", "low": 121, "high": 150, "color": "#FF0000"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coggan Power Zones"))
                .andExpect(jsonPath("$.zones", hasSize(6)))
                .andReturn();

        String zoneId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        // List
        mockMvc.perform(get("/api/zones/coach")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Get by ID
        mockMvc.perform(get("/api/zones/" + zoneId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coggan Power Zones"));

        // Update
        mockMvc.perform(put("/api/zones/coach/" + zoneId)
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Updated Power Zones",
                                    "sportType": "CYCLING",
                                    "referenceType": "FTP",
                                    "zones": [
                                        {"name": "Z1", "low": 0, "high": 55, "color": "#90EE90"},
                                        {"name": "Z2", "low": 56, "high": 75, "color": "#87CEEB"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Power Zones"));

        // Delete
        mockMvc.perform(delete("/api/zones/coach/" + zoneId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/zones/coach")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Non-coach cannot create zone system")
    void athleteCannotCreateZoneSystem() throws Exception {
        mockMvc.perform(post("/api/zones/coach")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "My Zones",
                                    "sportType": "CYCLING",
                                    "referenceType": "FTP",
                                    "zones": [{"name": "Z1", "low": 0, "high": 55}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}
