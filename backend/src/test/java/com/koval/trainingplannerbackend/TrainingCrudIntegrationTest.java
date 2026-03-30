package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for training CRUD operations:
 * - Create, read, update, delete trainings
 * - List, search, discover
 * - User isolation (user can only see own trainings)
 */
class TrainingCrudIntegrationTest extends BaseIntegrationTest {

    private String athleteToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        athleteToken = loginAthlete("athlete1");
    }

    private static final String CYCLING_TRAINING = """
            {
                "title": "Sweet Spot Intervals",
                "description": "2x20 sweet spot",
                "sportType": "CYCLING",
                "trainingType": "SWEET_SPOT",
                "estimatedTss": 75,
                "estimatedIf": 0.88,
                "estimatedDurationSeconds": 3600,
                "blocks": [
                    {"type": "WARMUP", "durationSeconds": 600, "label": "Easy spin", "intensityTarget": 55},
                    {"type": "STEADY", "durationSeconds": 1200, "label": "SS Block 1", "intensityTarget": 90},
                    {"type": "FREE", "durationSeconds": 300, "label": "Recovery", "intensityTarget": 50},
                    {"type": "STEADY", "durationSeconds": 1200, "label": "SS Block 2", "intensityTarget": 90},
                    {"type": "COOLDOWN", "durationSeconds": 300, "label": "Cool down", "intensityTarget": 45}
                ]
            }
            """;

    @Test
    @DisplayName("Create a cycling training and retrieve it")
    void createAndGetTraining() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CYCLING_TRAINING))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Sweet Spot Intervals"))
                .andExpect(jsonPath("$.sportType").value("CYCLING"))
                .andExpect(jsonPath("$.trainingType").value("SWEET_SPOT"))
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.blocks", hasSize(5)))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String trainingId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // GET by id
        mockMvc.perform(get("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sweet Spot Intervals"))
                .andExpect(jsonPath("$.blocks[0].type").value("WARMUP"))
                .andExpect(jsonPath("$.blocks[1].intensityTarget").value(90));
    }

    @Test
    @DisplayName("List trainings returns all user's trainings")
    void listTrainings() throws Exception {
        // Create two trainings
        mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CYCLING_TRAINING))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Recovery Ride", "sportType": "CYCLING", "trainingType": "RECOVERY",
                                 "blocks": [{"type": "STEADY", "durationSeconds": 2400, "label": "Z1", "intensityTarget": 50}]}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/trainings")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Update training modifies title and blocks")
    void updateTraining() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CYCLING_TRAINING))
                .andExpect(status().isCreated())
                .andReturn();

        String trainingId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(put("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sportType": "CYCLING", "title": "Updated SS Intervals", "estimatedTss": 80,
                                 "blocks": [{"type": "WARMUP", "durationSeconds": 600, "label": "Warm up", "intensityTarget": 55}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated SS Intervals"))
                .andExpect(jsonPath("$.blocks", hasSize(1)));
    }

    @Test
    @DisplayName("Delete training removes it")
    void deleteTraining() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CYCLING_TRAINING))
                .andExpect(status().isCreated())
                .andReturn();

        String trainingId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isNoContent());

        // Verify it's gone from list
        mockMvc.perform(get("/api/trainings")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("User cannot see another user's trainings")
    void userIsolation() throws Exception {
        // athlete1 creates a training
        mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CYCLING_TRAINING))
                .andExpect(status().isCreated());

        // athlete2 should see empty list
        String athlete2Token = loginAthlete("athlete2");
        mockMvc.perform(get("/api/trainings")
                        .header("Authorization", bearer(athlete2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Create running training with distance-based blocks")
    void createRunningTraining() throws Exception {
        mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tempo Run",
                                    "sportType": "RUNNING",
                                    "trainingType": "THRESHOLD",
                                    "estimatedTss": 60,
                                    "blocks": [
                                        {"type": "WARMUP", "distanceMeters": 2000, "label": "Easy jog", "intensityTarget": 70},
                                        {"type": "STEADY", "distanceMeters": 5000, "label": "Tempo", "intensityTarget": 100},
                                        {"type": "COOLDOWN", "distanceMeters": 1000, "label": "Cool down", "intensityTarget": 65}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sportType").value("RUNNING"))
                .andExpect(jsonPath("$.blocks[1].distanceMeters").value(5000));
    }

}
