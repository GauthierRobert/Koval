package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test covering the full application workflow:
 *
 * 1. Coach registers and completes onboarding
 * 2. Athlete registers and completes onboarding
 * 3. Coach creates a group
 * 4. Coach generates invite code
 * 5. Athlete redeems invite code → joins coach
 * 6. Coach creates a club
 * 7. Athlete joins the club
 * 8. Coach creates a training plan
 * 9. Coach assigns training to athlete with schedule date
 * 10. Athlete sees the assignment in their schedule/calendar
 * 11. Athlete self-schedules another workout
 * 12. Coach creates a club session linked to the training
 * 13. Athlete joins the club session
 * 14. Athlete completes the scheduled workout → saves session
 * 15. Session linked to scheduled workout
 * 16. Coach views athlete's sessions
 * 17. Athlete creates a race goal
 * 18. Coach views athlete's race goals
 * 19. Coach creates zone system
 * 20. Athlete views club leaderboard and stats
 */
class EndToEndFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Complete end-to-end flow: coach-athlete-club-training-session lifecycle")
    void fullEndToEndFlow() throws Exception {

        // ======= STEP 1: Coach registers and onboards =======
        String coachToken = loginUser("coach-jane", "Coach Jane", "COACH");

        mockMvc.perform(post("/api/auth/onboarding")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "COACH", "ftp": 310, "weightKg": 68}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("COACH"))
                .andExpect(jsonPath("$.user.ftp").value(310));

        // Refresh token from onboarding response
        MvcResult onboardResult = mockMvc.perform(post("/api/auth/onboarding")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "COACH", "ftp": 310, "weightKg": 68}
                                """))
                .andReturn();
        coachToken = objectMapper.readTree(
                onboardResult.getResponse().getContentAsString()).get("token").asText();

        // ======= STEP 2: Athlete registers and onboards =======
        String athleteToken = loginUser("athlete-bob", "Athlete Bob", "ATHLETE");

        MvcResult athleteOnboard = mockMvc.perform(post("/api/auth/onboarding")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "ATHLETE", "ftp": 250, "weightKg": 75}
                                """))
                .andReturn();
        athleteToken = objectMapper.readTree(
                athleteOnboard.getResponse().getContentAsString()).get("token").asText();

        // ======= STEP 3: Coach creates a group =======
        MvcResult groupResult = mockMvc.perform(post("/api/groups")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Performance Group", "maxAthletes": 15}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String groupId = objectMapper.readTree(
                groupResult.getResponse().getContentAsString()).get("id").asText();

        // ======= STEP 4: Coach generates invite code =======
        MvcResult inviteResult = mockMvc.perform(post("/api/coach/invite-codes")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groups": ["%s"], "maxUses": 20}
                                """.formatted(groupId)))
                .andExpect(status().isOk())
                .andReturn();

        String inviteCode = objectMapper.readTree(
                inviteResult.getResponse().getContentAsString()).get("code").asText();

        // ======= STEP 5: Athlete redeems invite code =======
        mockMvc.perform(post("/api/coach/redeem-invite")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(inviteCode)))
                .andExpect(status().isOk());

        // Verify coach sees athlete
        mockMvc.perform(get("/api/coach/athletes")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("athlete-bob"))
                .andExpect(jsonPath("$[0].displayName").value("Athlete Bob"));

        // ======= STEP 6: Coach creates a club =======
        MvcResult clubResult = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Jane's Cycling Academy",
                                    "description": "Performance cycling coaching",
                                    "location": "Lyon",
                                    "visibility": "PUBLIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String clubId = objectMapper.readTree(
                clubResult.getResponse().getContentAsString()).get("id").asText();

        // ======= STEP 7: Athlete joins the club =======
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Verify membership
        mockMvc.perform(get("/api/clubs/" + clubId + "/members")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(jsonPath("$", hasSize(2))); // coach + athlete

        // ======= STEP 8: Coach creates a training plan =======
        MvcResult trainingResult = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Over-Under Intervals",
                                    "description": "Sweet spot / threshold alternation",
                                    "sportType": "CYCLING",
                                    "trainingType": "SWEET_SPOT",
                                    "estimatedTss": 78,
                                    "estimatedIf": 0.89,
                                    "estimatedDurationSeconds": 3600,
                                    "blocks": [
                                        {"type": "WARMUP", "durationSeconds": 600, "label": "Progressive warm-up", "intensityStart": 45, "intensityEnd": 75},
                                        {"type": "STEADY", "durationSeconds": 600, "label": "Over", "intensityTarget": 105},
                                        {"type": "STEADY", "durationSeconds": 600, "label": "Under", "intensityTarget": 88},
                                        {"type": "STEADY", "durationSeconds": 600, "label": "Over", "intensityTarget": 105},
                                        {"type": "STEADY", "durationSeconds": 600, "label": "Under", "intensityTarget": 88},
                                        {"type": "COOLDOWN", "durationSeconds": 600, "label": "Easy spin down", "intensityTarget": 45}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String trainingId = objectMapper.readTree(
                trainingResult.getResponse().getContentAsString()).get("id").asText();

        // ======= STEP 9: Coach assigns training to athlete =======
        LocalDate assignedDate = LocalDate.now().plusDays(2);

        mockMvc.perform(post("/api/coach/assign")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "athleteIds": ["athlete-bob"],
                                    "scheduledDate": "%s",
                                    "notes": "Focus on smooth transitions between over and under"
                                }
                                """.formatted(trainingId, assignedDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        // ======= STEP 10: Athlete sees assignment in schedule =======
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", assignedDate.minusDays(1).toString())
                        .param("end", assignedDate.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].notes").value("Focus on smooth transitions between over and under"));

        // Athlete also sees it in training list (coach's training visible via assignment)
        // Verify the training exists
        mockMvc.perform(get("/api/trainings/" + trainingId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Over-Under Intervals"));

        // ======= STEP 11: Athlete self-schedules another workout =======
        MvcResult selfTrainingResult = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Easy Recovery",
                                    "sportType": "CYCLING",
                                    "trainingType": "RECOVERY",
                                    "estimatedTss": 25,
                                    "blocks": [{"type": "STEADY", "durationSeconds": 2400, "label": "Z1 spin", "intensityTarget": 50}]
                                }
                                """))
                .andReturn();

        String selfTrainingId = objectMapper.readTree(
                selfTrainingResult.getResponse().getContentAsString()).get("id").asText();

        LocalDate selfDate = LocalDate.now().plusDays(3);
        mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s", "notes": "Active recovery day"}
                                """.formatted(selfTrainingId, selfDate)))
                .andExpect(status().isCreated());

        // ======= STEP 12: Coach creates club session linked to training =======
        LocalDateTime sessionTime = assignedDate.atTime(18, 0);
        MvcResult clubSessionResult = mockMvc.perform(post("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Group Over-Under Session",
                                    "sport": "CYCLING",
                                    "scheduledAt": "%s",
                                    "location": "Velodrome",
                                    "description": "Do the over-under workout together",
                                    "linkedTrainingId": "%s",
                                    "maxParticipants": 15
                                }
                                """.formatted(sessionTime, trainingId)))
                .andExpect(status().isOk())
                .andReturn();

        String clubSessionId = objectMapper.readTree(
                clubSessionResult.getResponse().getContentAsString()).get("id").asText();

        // ======= STEP 13: Athlete joins the club session =======
        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + clubSessionId + "/join")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds", hasItem("athlete-bob")));

        // ======= STEP 14: Athlete completes workout and saves session =======
        MvcResult sessionSaveResult = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "title": "Over-Under Intervals",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 3600,
                                    "avgPower": 245.0,
                                    "avgHR": 162.0,
                                    "avgCadence": 88.0,
                                    "avgSpeed": 35.5,
                                    "sportType": "CYCLING",
                                    "tss": 78.0,
                                    "intensityFactor": 0.89,
                                    "blockSummaries": [
                                        {"label": "Warm-up", "type": "WARMUP", "durationSeconds": 600, "targetPower": 140, "actualPower": 142, "actualCadence": 85, "actualHR": 120},
                                        {"label": "Over", "type": "STEADY", "durationSeconds": 600, "targetPower": 262, "actualPower": 258, "actualCadence": 90, "actualHR": 170},
                                        {"label": "Under", "type": "STEADY", "durationSeconds": 600, "targetPower": 220, "actualPower": 222, "actualCadence": 88, "actualHR": 155}
                                    ]
                                }
                                """.formatted(trainingId, LocalDateTime.now())))
                .andExpect(status().isOk())
                .andReturn();

        String sessionId = objectMapper.readTree(
                sessionSaveResult.getResponse().getContentAsString()).get("id").asText();

        // Rate the session
        mockMvc.perform(patch("/api/sessions/" + sessionId)
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rpe": 7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpe").value(7));

        // ======= STEP 15: Verify session in athlete's history =======
        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Over-Under Intervals"))
                .andExpect(jsonPath("$[0].avgPower").value(245.0));

        // ======= STEP 16: Coach views athlete's sessions =======
        mockMvc.perform(get("/api/coach/athletes/athlete-bob/sessions")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Over-Under Intervals"));

        // ======= STEP 17: Athlete creates a race goal =======
        mockMvc.perform(post("/api/goals")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "La Marmotte",
                                    "sport": "CYCLING",
                                    "raceDate": "%s",
                                    "priority": "A",
                                    "distance": "174km",
                                    "location": "Alpe d'Huez",
                                    "targetTime": "7:30:00",
                                    "notes": "Key goal for the season"
                                }
                                """.formatted(LocalDate.now().plusMonths(4))))
                .andExpect(status().isCreated());

        // ======= STEP 18: Coach views athlete's race goals =======
        mockMvc.perform(get("/api/coach/athletes/athlete-bob/goals")
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("La Marmotte"));

        // ======= STEP 19: Coach creates zone system =======
        mockMvc.perform(post("/api/zones/coach")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Academy Power Zones",
                                    "sportType": "CYCLING",
                                    "referenceType": "FTP",
                                    "referenceName": "FTP",
                                    "zones": [
                                        {"name": "Z1", "low": 0, "high": 55, "color": "#90EE90"},
                                        {"name": "Z2", "low": 56, "high": 75, "color": "#87CEEB"},
                                        {"name": "Z3", "low": 76, "high": 90, "color": "#FFD700"},
                                        {"name": "Z4", "low": 91, "high": 105, "color": "#FFA500"},
                                        {"name": "Z5", "low": 106, "high": 120, "color": "#FF4500"}
                                    ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Academy Power Zones"));

        // ======= STEP 20: Club stats and leaderboard =======
        mockMvc.perform(get("/api/clubs/" + clubId + "/stats/weekly")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clubs/" + clubId + "/leaderboard")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());

        // ======= VERIFY: Full schedule view shows both assignments =======
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", LocalDate.now().toString())
                        .param("end", LocalDate.now().plusDays(7).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))); // coach-assigned + self-scheduled

        // ======= VERIFY: PMC data available =======
        mockMvc.perform(get("/api/sessions/pmc")
                        .header("Authorization", bearer(athleteToken))
                        .param("from", LocalDate.now().minusDays(30).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // ======= VERIFY: Coach PMC for athlete =======
        mockMvc.perform(get("/api/coach/athletes/athlete-bob/pmc")
                        .header("Authorization", bearer(coachToken))
                        .param("from", LocalDate.now().minusDays(30).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
