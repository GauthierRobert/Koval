package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the self-scheduling and session lifecycle:
 * 1. Athlete self-schedules a workout
 * 2. Athlete views schedule/calendar
 * 3. Athlete reschedules a workout
 * 4. Athlete completes a workout (records session)
 * 5. Session linked to schedule
 * 6. Skip a workout
 * 7. PMC data retrieval
 */
class ScheduleAndSessionFlowIntegrationTest extends BaseIntegrationTest {

    private String athleteToken;
    private String trainingId;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        athleteToken = loginAthlete("athlete1");

        // Create a training to schedule
        MvcResult result = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Threshold Test",
                                    "sportType": "CYCLING",
                                    "trainingType": "THRESHOLD",
                                    "estimatedTss": 65,
                                    "blocks": [
                                        {"type": "WARMUP", "durationSeconds": 600, "label": "Easy", "intensityTarget": 55},
                                        {"type": "STEADY", "durationSeconds": 1200, "label": "Threshold", "intensityTarget": 100},
                                        {"type": "COOLDOWN", "durationSeconds": 300, "label": "Cool", "intensityTarget": 45}
                                    ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        trainingId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Athlete self-schedules a workout and sees it in calendar")
    void selfScheduleAndView() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        // Schedule
        MvcResult scheduleResult = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s", "notes": "Morning session"}
                                """.formatted(trainingId, date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trainingId").value(trainingId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        // View schedule
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", date.minusDays(1).toString())
                        .param("end", date.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trainingId").value(trainingId))
                .andExpect(jsonPath("$[0].notes").value("Morning session"));
    }

    @Test
    @DisplayName("Athlete reschedules a workout to a new date")
    void rescheduleWorkout() throws Exception {
        LocalDate originalDate = LocalDate.now().plusDays(1);
        LocalDate newDate = LocalDate.now().plusDays(3);

        MvcResult result = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s"}
                                """.formatted(trainingId, originalDate)))
                .andReturn();

        String scheduleId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        // Reschedule
        mockMvc.perform(patch("/api/schedule/" + scheduleId + "/reschedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledDate": "%s"}
                                """.formatted(newDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledDate").value(newDate.toString()));

        // Not on original date
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", originalDate.toString())
                        .param("end", originalDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Athlete marks workout as completed")
    void markCompleted() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        MvcResult result = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s"}
                                """.formatted(trainingId, date)))
                .andReturn();

        String scheduleId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/schedule/" + scheduleId + "/complete")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Athlete skips a workout")
    void skipWorkout() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        MvcResult result = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s"}
                                """.formatted(trainingId, date)))
                .andReturn();

        String scheduleId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/schedule/" + scheduleId + "/skip")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
    }

    @Test
    @DisplayName("Athlete deletes a scheduled workout")
    void deleteScheduledWorkout() throws Exception {
        LocalDate date = LocalDate.now().plusDays(1);

        MvcResult result = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s"}
                                """.formatted(trainingId, date)))
                .andReturn();

        String scheduleId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/schedule/" + scheduleId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", date.minusDays(1).toString())
                        .param("end", date.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // --- Session (completed workout) tests ---

    @Test
    @DisplayName("Save a completed session and list it")
    void saveAndListSession() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "title": "Threshold Test",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 2100,
                                    "avgPower": 250.0,
                                    "avgHR": 165.0,
                                    "avgCadence": 90.0,
                                    "sportType": "CYCLING",
                                    "tss": 65.0,
                                    "intensityFactor": 0.95
                                }
                                """.formatted(trainingId, LocalDateTime.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Threshold Test"))
                .andExpect(jsonPath("$.avgPower").value(250.0));

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Get sessions for calendar view")
    void getSessionsForCalendar() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Morning Ride",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 3600,
                                    "avgPower": 200.0,
                                    "sportType": "CYCLING",
                                    "tss": 50.0
                                }
                                """.formatted(now)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sessions/calendar")
                        .header("Authorization", bearer(athleteToken))
                        .param("start", now.toLocalDate().minusDays(1).toString())
                        .param("end", now.toLocalDate().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Link session to scheduled workout")
    void linkSessionToSchedule() throws Exception {
        LocalDate date = LocalDate.now();

        // Schedule a workout
        MvcResult schedResult = mockMvc.perform(post("/api/schedule")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s", "scheduledDate": "%s"}
                                """.formatted(trainingId, date)))
                .andReturn();

        String scheduleId = objectMapper.readTree(
                schedResult.getResponse().getContentAsString()).get("id").asText();

        // Save a session
        MvcResult sessionResult = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "trainingId": "%s",
                                    "title": "Threshold Test",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 2100,
                                    "avgPower": 250.0,
                                    "sportType": "CYCLING",
                                    "tss": 65.0
                                }
                                """.formatted(trainingId, LocalDateTime.now())))
                .andReturn();

        String sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("id").asText();

        // Link session to schedule
        mockMvc.perform(post("/api/sessions/" + sessionId + "/link/" + scheduleId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledWorkoutId").value(scheduleId));
    }

    @Test
    @DisplayName("Patch session with RPE rating")
    void patchSessionRpe() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Hard Ride",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 3600,
                                    "avgPower": 280.0,
                                    "sportType": "CYCLING"
                                }
                                """.formatted(LocalDateTime.now())))
                .andReturn();

        String sessionId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/sessions/" + sessionId)
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rpe": 8}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpe").value(8));
    }

    @Test
    @DisplayName("Delete a session")
    void deleteSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "To Delete",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 1800,
                                    "sportType": "CYCLING"
                                }
                                """.formatted(LocalDateTime.now())))
                .andReturn();

        String sessionId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/sessions/" + sessionId)
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("PMC data endpoint returns list")
    void pmcData() throws Exception {
        // Save a session to have some data
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Test Ride",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 3600,
                                    "sportType": "CYCLING",
                                    "tss": 70.0
                                }
                                """.formatted(LocalDateTime.now())))
                .andExpect(status().isOk());

        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/sessions/pmc")
                        .header("Authorization", bearer(athleteToken))
                        .param("from", today.minusDays(30).toString())
                        .param("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Session user isolation: user can't see other's sessions")
    void sessionUserIsolation() throws Exception {
        // athlete1 saves a session
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "My Ride",
                                    "completedAt": "%s",
                                    "totalDurationSeconds": 1800,
                                    "sportType": "CYCLING"
                                }
                                """.formatted(LocalDateTime.now())))
                .andExpect(status().isOk());

        // athlete2 should see empty list
        String athlete2Token = loginAthlete("athlete2");
        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", bearer(athlete2Token)))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
