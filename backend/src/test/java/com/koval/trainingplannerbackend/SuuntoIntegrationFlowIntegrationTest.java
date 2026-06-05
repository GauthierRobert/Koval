package com.koval.trainingplannerbackend;

import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.integration.suunto.SuuntoApiClient;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end flow for the Suunto integration: unauthenticated webhook ingestion with dedup,
 * and the authenticated import-history endpoint. The Suunto HTTP client is mocked — no live API.
 */
class SuuntoIntegrationFlowIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    private SuuntoApiClient suuntoApiClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompletedSessionRepository sessionRepository;

    private static final Map<String, Object> WORKOUT = Map.of(
            "workoutKey", "w1",
            "workoutName", "Evening Ride",
            "activityId", 3,
            "startTime", 1750000000000L,
            "totalTime", 3600.0);

    /** Dev-logs the athlete in and stamps a valid Suunto link on the user. Returns the JWT. */
    private String connectSuunto(String userId, String suuntoUsername) throws Exception {
        String token = loginAthlete(userId);
        User user = userRepository.findById(userId).orElseThrow();
        user.setSuuntoUserId(suuntoUsername);
        user.setSuuntoAccessToken("token");
        user.setSuuntoTokenExpiresAt(Instant.now().getEpochSecond() + 3600); // no refresh needed
        userRepository.save(user);
        return token;
    }

    @Test
    void webhook_newWorkout_importsAndDedupsOnRefire() throws Exception {
        connectSuunto("athlete1", "suunto-athlete1");
        when(suuntoApiClient.fetchWorkout(anyString(), eq("w1"))).thenReturn(WORKOUT);
        when(suuntoApiClient.exportFit(anyString(), anyString())).thenReturn(Optional.empty());

        // Unauthenticated — permitted in SecurityConfig
        mockMvc.perform(post("/api/integration/suunto/webhook")
                        .param("username", "suunto-athlete1")
                        .param("workoutid", "w1"))
                .andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CompletedSession> sessions = sessionRepository.findSuuntoActivityIdsByUserId("athlete1");
            assertEquals(1, sessions.size());
            assertEquals("w1", sessions.get(0).getSuuntoActivityId());
        });

        // Re-fire the same notification — must not create a duplicate
        mockMvc.perform(post("/api/integration/suunto/webhook")
                        .param("username", "suunto-athlete1")
                        .param("workoutid", "w1"))
                .andExpect(status().isOk());

        Awaitility.await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() ->
                        assertEquals(1, sessionRepository.findSuuntoActivityIdsByUserId("athlete1").size()));
    }

    @Test
    void webhook_unknownUser_acksWithoutImporting() throws Exception {
        mockMvc.perform(post("/api/integration/suunto/webhook")
                        .param("username", "nobody")
                        .param("workoutid", "w1"))
                .andExpect(status().isOk());
    }

    @Test
    void importHistory_authenticated_returnsSyncResult() throws Exception {
        String token = connectSuunto("athlete2", "suunto-athlete2");

        when(suuntoApiClient.listWorkouts(anyString(), anyLong())).thenReturn(List.of(WORKOUT));
        when(suuntoApiClient.exportFit(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/integration/suunto/import-history")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFetched").value(1))
                .andExpect(jsonPath("$.newlyImported").value(1))
                .andExpect(jsonPath("$.skippedDuplicates").value(0));

        assertEquals(1, sessionRepository.findSuuntoActivityIdsByUserId("athlete2").size());
    }

    @Test
    void importHistory_notConnected_fails() throws Exception {
        String token = loginAthlete("athlete3");

        mockMvc.perform(post("/api/integration/suunto/import-history")
                        .header("Authorization", bearer(token)))
                .andExpect(status().is4xxClientError());
    }
}
