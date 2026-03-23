package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the ReceivedTraining system:
 * 1. Coach assigns training to athlete via group → COACH_GROUP origin
 * 2. Coach assigns training from club → CLUB origin
 * 3. Club session with linked training → CLUB_SESSION virtual origin
 * 4. Multiple origins — athlete receives from all 3 flows
 * 5. Deduplication — real entry takes priority over virtual club session
 * 6. Duplicate assignment is idempotent
 * 7. Athlete can access received training via GET /api/trainings/{id}
 */
class ReceivedTrainingFlowIntegrationTest extends BaseIntegrationTest {

    private String coachToken;
    private String athleteToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        coachToken = loginCoach("coach1");
        athleteToken = loginAthlete("athlete1");
    }

    @Test
    @DisplayName("Coach assigns training via group → received with COACH_GROUP origin")
    void coachGroupAssignment() throws Exception {
        String groupId = setupCoachAthleteRelationship();
        String trainingId = createTraining(coachToken, "Group Workout");

        // Coach assigns training to athlete
        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "groupId": "%s"
                                }
                                """.formatted(trainingId, LocalDate.now().plusDays(1), groupId)))
                .andExpect(status().isOk());

        // Athlete checks received trainings
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].origin").value("COACH_GROUP"))
                .andExpect(jsonPath("$[0].assignedByName").value("coach1-name"))
                .andExpect(jsonPath("$[0].originName").value("default group"));
    }

    @Test
    @DisplayName("Coach assigns training from club → received with CLUB origin")
    void clubAssignment() throws Exception {
        String clubId = createClub("Cycling Club", "PUBLIC");
        joinClub(athleteToken, clubId);

        String trainingId = createTraining(coachToken, "Club Workout");

        // Coach assigns via club
        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "clubId": "%s"
                                }
                                """.formatted(trainingId, LocalDate.now().plusDays(1), clubId)))
                .andExpect(status().isOk());

        // Athlete checks received trainings
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].origin").value("CLUB"))
                .andExpect(jsonPath("$[0].originName").value("Cycling Club"));
    }

    @Test
    @DisplayName("Club session with linked training → virtual CLUB_SESSION origin")
    void clubSessionLinkedTraining() throws Exception {
        String clubId = createClub("Session Club", "PUBLIC");
        joinClub(athleteToken, clubId);

        String trainingId = createTraining(coachToken, "Session Workout");
        String sessionId = createClubSession(coachToken, clubId, "Morning Ride");

        // Link training to session
        mockMvc.perform(put("/api/clubs/" + clubId + "/sessions/" + sessionId + "/link-training")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s"}
                                """.formatted(trainingId)))
                .andExpect(status().isOk());

        // Athlete joins the session
        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());

        // Athlete checks received trainings — virtual entry
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].origin").value("CLUB_SESSION"))
                .andExpect(jsonPath("$[0].originName").value("Session Club"))
                .andExpect(jsonPath("$[0].id", startsWith("session:")));
    }

    @Test
    @DisplayName("Athlete receives trainings from all 3 origins")
    void multipleOrigins() throws Exception {
        // Flow 1: Coach group
        String groupId = setupCoachAthleteRelationship();
        String training1 = createTraining(coachToken, "Group Training");
        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "groupId": "%s"
                                }
                                """.formatted(training1, LocalDate.now().plusDays(1), groupId)))
                .andExpect(status().isOk());

        // Flow 2: Club assignment
        String clubId = createClub("Multi Club", "PUBLIC");
        joinClub(athleteToken, clubId);
        String training2 = createTraining(coachToken, "Club Training");
        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "clubId": "%s"
                                }
                                """.formatted(training2, LocalDate.now().plusDays(2), clubId)))
                .andExpect(status().isOk());

        // Flow 3: Club session
        String training3 = createTraining(coachToken, "Session Training");
        String sessionId = createClubSession(coachToken, clubId, "Evening Ride");
        mockMvc.perform(put("/api/clubs/" + clubId + "/sessions/" + sessionId + "/link-training")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s"}
                                """.formatted(training3)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());

        // Verify all 3 entries
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.trainingId=='%s')].origin".formatted(training1), hasItem("COACH_GROUP")))
                .andExpect(jsonPath("$[?(@.trainingId=='%s')].origin".formatted(training2), hasItem("CLUB")))
                .andExpect(jsonPath("$[?(@.trainingId=='%s')].origin".formatted(training3), hasItem("CLUB_SESSION")));
    }

    @Test
    @DisplayName("Real entry takes priority over virtual club session for same training")
    void deduplicationRealWinsOverVirtual() throws Exception {
        // Setup coach-athlete via group + club
        String groupId = setupCoachAthleteRelationship();
        String clubId = createClub("Dedup Club", "PUBLIC");
        joinClub(athleteToken, clubId);

        String trainingId = createTraining(coachToken, "Dedup Training");

        // Real assignment via coach group
        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "groupId": "%s"
                                }
                                """.formatted(trainingId, LocalDate.now().plusDays(1), groupId)))
                .andExpect(status().isOk());

        // Same training linked to club session, athlete joins
        String sessionId = createClubSession(coachToken, clubId, "Dedup Session");
        mockMvc.perform(put("/api/clubs/" + clubId + "/sessions/" + sessionId + "/link-training")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s"}
                                """.formatted(trainingId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());

        // Only 1 entry — real COACH_GROUP wins over virtual CLUB_SESSION
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].origin").value("COACH_GROUP"));
    }

    @Test
    @DisplayName("Duplicate assignment is idempotent — still 1 entry")
    void duplicateAssignmentIdempotent() throws Exception {
        String groupId = setupCoachAthleteRelationship();
        String trainingId = createTraining(coachToken, "Idempotent Workout");
        LocalDate date = LocalDate.now().plusDays(1);

        // Assign twice
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/coach/assign")
                            .header("Authorization", bearer(coachToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "trainingId": "%s",
                                        "athleteIds": ["athlete1"],
                                        "scheduledDate": "%s",
                                        "groupId": "%s"
                                    }
                                    """.formatted(trainingId, date, groupId)))
                    .andExpect(status().isOk());
        }

        // Still only 1 received training entry
        mockMvc.perform(get("/api/trainings/received")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId));
    }

    @Test
    @DisplayName("Athlete can access received training; unrelated athlete gets 403")
    void receivedTrainingAccessControl() throws Exception {
        String groupId = setupCoachAthleteRelationship();
        String trainingId = createTraining(coachToken, "Access Controlled");

        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete1"],
                                    "scheduledDate": "%s",
                                    "groupId": "%s"
                                }
                                """.formatted(trainingId, LocalDate.now().plusDays(1), groupId)))
                .andExpect(status().isOk());

        // Athlete1 can access the training
        mockMvc.perform(get("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trainingId));

        // Unrelated athlete gets 403
        String unrelatedToken = loginAthlete("athlete2");
        mockMvc.perform(get("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(unrelatedToken)))
                .andExpect(status().isForbidden());
    }

    // --- Helper methods ---

    private String setupCoachAthleteRelationship() throws Exception {
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

        return groupId;
    }

    private String createTraining(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s",
                                    "sportType": "CYCLING",
                                    "trainingType": "ENDURANCE",
                                    "blocks": [{"type": "STEADY", "durationSeconds": 3600, "label": "Main", "intensityTarget": 70}]
                                }
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createClub(String name, String visibility) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "description": "Test club", "location": "Test City", "visibility": "%s"}
                                """.formatted(name, visibility)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void joinClub(String token, String clubId) throws Exception {
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private String createClubSession(String token, String clubId, String title) throws Exception {
        LocalDateTime sessionTime = LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);
        MvcResult result = mockMvc.perform(post("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "%s",
                                    "sport": "CYCLING",
                                    "scheduledAt": "%s",
                                    "maxParticipants": 20
                                }
                                """.formatted(title, sessionTime)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
