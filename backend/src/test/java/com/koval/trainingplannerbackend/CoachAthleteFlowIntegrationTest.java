package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the full coach-athlete workflow:
 * 1. Coach creates account
 * 2. Coach creates a group
 * 3. Coach generates an invite code
 * 4. Athlete creates account and redeems invite code
 * 5. Coach sees athlete in their athlete list
 * 6. Coach creates a training
 * 7. Coach assigns training to athlete
 * 8. Athlete sees assignment in schedule/calendar
 * 9. Athlete completes the workout
 * 10. Coach can view athlete's completed sessions
 */
class CoachAthleteFlowIntegrationTest extends BaseIntegrationTest {

    private String coachToken;
    private String athleteToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        coachToken = loginCoach("coach1");
        athleteToken = loginAthlete("athlete1");
    }

    // --- Group Management ---

    @Test
    @DisplayName("Coach creates a group")
    void coachCreatesGroup() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Elite Squad", "maxAthletes": 10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("elite squad"))
                .andExpect(jsonPath("$.maxAthletes").value(10))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    @DisplayName("Coach lists their groups")
    void coachListsGroups() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Group A", "maxAthletes": 5}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/groups")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("group a"));
    }

    @Test
    @DisplayName("Coach renames and deletes a group")
    void coachRenamesAndDeletesGroup() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Old Name", "maxAthletes": 5}
                                """))
                .andReturn();

        String groupId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        // Rename
        mockMvc.perform(put("/api/groups/" + groupId)
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "New Name"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new name"));

        // Delete
        mockMvc.perform(delete("/api/groups/" + groupId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isNoContent());
    }

    // --- Invite Code Flow ---

    @Test
    @DisplayName("Full invite code flow: generate, list, redeem, verify athlete")
    void inviteCodeFlow() throws Exception {
        // Coach creates group
        MvcResult groupResult = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Beginners", "maxAthletes": 20}
                                """))
                .andReturn();

        String groupId = objectMapper.readTree(
                groupResult.getResponse().getContentAsString()).get("id").asText();

        // Coach generates invite code
        MvcResult inviteResult = mockMvc.perform(post("/api/coach/invite-codes")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groups": ["%s"], "maxUses": 5}
                                """.formatted(groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.maxUses").value(5))
                .andReturn();

        String inviteCode = objectMapper.readTree(
                inviteResult.getResponse().getContentAsString()).get("code").asText();

        // Coach can list invite codes
        mockMvc.perform(get("/api/coach/invite-codes")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value(inviteCode));

        // Athlete redeems the invite code
        mockMvc.perform(post("/api/coach/redeem-invite")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(inviteCode)))
                .andExpect(status().isOk());

        // Coach now sees the athlete in their athlete list
        mockMvc.perform(get("/api/coach/athletes")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("athlete1"));
    }

    // --- Training Assignment Flow ---

    @Test
    @DisplayName("Full flow: coach creates training, assigns to athlete, athlete sees in schedule")
    void trainingAssignmentFlow() throws Exception {
        // Setup: athlete joins coach via invite
        setupCoachAthleteRelationship();

        // Coach creates a training
        MvcResult trainingResult = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "VO2max Intervals",
                                    "sportType": "CYCLING",
                                    "trainingType": "VO2MAX",
                                    "estimatedTss": 90,
                                    "estimatedIf": 0.95,
                                    "blocks": [
                                        {"type": "WARMUP", "durationSeconds": 600, "label": "Warm up", "intensityTarget": 55},
                                        {"type": "INTERVAL", "durationSeconds": 300, "label": "VO2max", "intensityTarget": 120},
                                        {"type": "FREE", "durationSeconds": 300, "label": "Recovery", "intensityTarget": 40},
                                        {"type": "INTERVAL", "durationSeconds": 300, "label": "VO2max", "intensityTarget": 120},
                                        {"type": "FREE", "durationSeconds": 300, "label": "Recovery", "intensityTarget": 40},
                                        {"type": "INTERVAL", "durationSeconds": 300, "label": "VO2max", "intensityTarget": 120},
                                        {"type": "COOLDOWN", "durationSeconds": 600, "label": "Cool down", "intensityTarget": 45}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String trainingId = objectMapper.readTree(
                trainingResult.getResponse().getContentAsString()).get("id").asText();

        // Coach assigns training to athlete
        LocalDate targetDate = LocalDate.now().plusDays(2);
        MvcResult assignResult = mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "notes": "Push hard on the intervals!"
                                }
                                """.formatted(trainingId, targetDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].athleteId").value("athlete1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn();

        // Athlete sees assignment in their schedule
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", targetDate.minusDays(1).toString())
                        .param("end", targetDate.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].notes").value("Push hard on the intervals!"));
    }

    @Test
    @DisplayName("Coach views athlete schedule in a date range")
    void coachViewsAthleteSchedule() throws Exception {
        setupCoachAthleteRelationship();

        // Create and assign training
        String trainingId = createTraining(coachToken, "Endurance Ride", "CYCLING", "ENDURANCE");
        LocalDate date = LocalDate.now().plusDays(3);

        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "athleteIds": ["athlete1"], "scheduledDate": "%s"}
                                """.formatted(trainingId, date)))
                .andExpect(status().isOk());

        // Coach views athlete's schedule
        mockMvc.perform(get("/api/coach/schedule/athlete1")
                        .header("Authorization", bearer(coachToken))
                        .param("start", date.minusDays(1).toString())
                        .param("end", date.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Coach removes athlete from roster")
    void coachRemovesAthlete() throws Exception {
        setupCoachAthleteRelationship();

        // Verify athlete exists
        mockMvc.perform(get("/api/coach/athletes")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(jsonPath("$", hasSize(1)));

        // Remove athlete
        mockMvc.perform(delete("/api/coach/athletes/athlete1")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isNoContent());

        // Verify removed
        mockMvc.perform(get("/api/coach/athletes")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Coach deactivates invite code")
    void coachDeactivatesInviteCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/coach/invite-codes")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groups": [], "maxUses": 10}
                                """))
                .andReturn();

        String codeId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/coach/invite-codes/" + codeId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isNoContent());
    }

    // --- Helpers ---

    private void setupCoachAthleteRelationship() throws Exception {
        // Create group
        MvcResult groupResult = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Default Group", "maxAthletes": 50}
                                """))
                .andReturn();

        String groupId = objectMapper.readTree(
                groupResult.getResponse().getContentAsString()).get("id").asText();

        // Generate invite code
        MvcResult inviteResult = mockMvc.perform(post("/api/coach/invite-codes")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groups": ["%s"], "maxUses": 10}
                                """.formatted(groupId)))
                .andReturn();

        String inviteCode = objectMapper.readTree(
                inviteResult.getResponse().getContentAsString()).get("code").asText();

        // Athlete redeems
        mockMvc.perform(post("/api/coach/redeem-invite")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(inviteCode)))
                .andExpect(status().isOk());
    }

    private String createTraining(String token, String title, String sportType, String trainingType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s",
                                    "sportType": "%s",
                                    "trainingType": "%s",
                                    "blocks": [{"type": "STEADY", "durationSeconds": 3600, "label": "Main", "intensityTarget": 70}]
                                }
                                """.formatted(title, sportType, trainingType)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
