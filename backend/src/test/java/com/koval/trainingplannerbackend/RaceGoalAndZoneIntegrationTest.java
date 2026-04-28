package com.koval.trainingplannerbackend;

import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.race.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for race goals and zone system CRUD.
 */
class RaceGoalAndZoneIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RaceRepository raceRepository;

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
                                    "priority": "A",
                                    "distance": "140.6 miles",
                                    "location": "Nice, France",
                                    "targetTime": "10:30:00",
                                    "notes": "Main goal race"
                                }
                                """))
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
                                    "priority": "A",
                                    "targetTime": "10:00:00"
                                }
                                """))
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
                                {"title": "My Race", "sport": "CYCLING", "priority": "B"}
                                """))
                .andExpect(status().isCreated());

        String athlete2Token = loginAthlete("athlete2");
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athlete2Token)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Listed goal exposes the linked race's scheduledDate as its date")
    void goalListIncludesLinkedRaceDate() throws Exception {
        // Seed a race in the catalog with a known scheduledDate
        String scheduledDate = LocalDate.now().plusMonths(6).toString();
        Race race = new Race();
        race.setTitle("Ironman Nice 2026");
        race.setSport("TRIATHLON");
        race.setLocation("Nice, France");
        race.setDistance("140.6 miles");
        race.setScheduledDate(scheduledDate);
        Race savedRace = raceRepository.save(race);

        // Create a goal linked to that race (no raceDate in payload)
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Sub-10 Ironman",
                                    "sport": "TRIATHLON",
                                    "priority": "A",
                                    "raceId": "%s"
                                }
                                """.formatted(savedRace.getId())))
                .andExpect(status().isCreated());

        // Listing the goal should embed the race and expose its scheduledDate
        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].raceId").value(savedRace.getId()))
                .andExpect(jsonPath("$[0].race.scheduledDate").value(scheduledDate));
    }

    @Test
    @DisplayName("Goal without a linked race has no date in the response")
    void goalWithoutRaceHasNoDate() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Unscheduled goal", "sport": "RUNNING", "priority": "C"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/goals")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].race", is(org.hamcrest.Matchers.nullValue())));
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
