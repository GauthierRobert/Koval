package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the in-app notification flow:
 * - FCM token register / unregister
 * - Notification preferences (defaults, update, persistence)
 * - Notification center listing & unread count are unauthenticated for unknown ids
 */
class NotificationFlowIntegrationTest extends BaseIntegrationTest {

    private String athleteToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        athleteToken = loginAthlete("athlete1");
    }

    @Test
    @DisplayName("Register and unregister FCM token")
    void registerAndUnregisterToken() throws Exception {
        mockMvc.perform(post("/api/notifications/register-token")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token-1"}
                                """))
                .andExpect(status().isOk());

        // Re-registering the same token should be idempotent
        mockMvc.perform(post("/api/notifications/register-token")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token-1"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notifications/unregister-token")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token": "fcm-token-1"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET preferences returns defaults for new user")
    void getPreferences_returnsDefaults() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workoutAssigned").value(true))
                .andExpect(jsonPath("$.workoutReminder").value(true))
                .andExpect(jsonPath("$.workoutCompletedCoach").value(true))
                .andExpect(jsonPath("$.clubSessionCreated").value(true))
                .andExpect(jsonPath("$.clubSessionCancelled").value(true))
                .andExpect(jsonPath("$.waitingListPromoted").value(true))
                .andExpect(jsonPath("$.planActivated").value(true))
                .andExpect(jsonPath("$.clubAnnouncement").value(true))
                .andExpect(jsonPath("$.openSessionCreated").value(true));
    }

    @Test
    @DisplayName("PUT preferences updates flags and is persisted")
    void updatePreferences_persists() throws Exception {
        mockMvc.perform(put("/api/notifications/preferences")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "workoutAssigned": false,
                                    "workoutReminder": false,
                                    "clubAnnouncement": true,
                                    "openSessionCreated": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workoutAssigned").value(false))
                .andExpect(jsonPath("$.workoutReminder").value(false))
                .andExpect(jsonPath("$.openSessionCreated").value(false));

        // Verify persistence
        mockMvc.perform(get("/api/notifications/preferences")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(jsonPath("$.workoutAssigned").value(false))
                .andExpect(jsonPath("$.openSessionCreated").value(false))
                // Unspecified fields should default back to true via canonical constructor
                .andExpect(jsonPath("$.clubSessionCreated").value(true));
    }

    @Test
    @DisplayName("PUT preferences with omitted fields keeps them at default true")
    void updatePreferences_omittedFieldsDefaultTrue() throws Exception {
        mockMvc.perform(put("/api/notifications/preferences")
                        .header("Authorization", bearer(athleteToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workoutReminder": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workoutReminder").value(false))
                .andExpect(jsonPath("$.workoutAssigned").value(true))
                .andExpect(jsonPath("$.planActivated").value(true));
    }

    @Test
    @DisplayName("Notification center: empty by default, unread count is zero")
    void notificationCenter_emptyByDefault() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("Mark-all-read returns zero when nothing unread")
    void markAllRead_returnsZeroForEmpty() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(0));
    }

    @Test
    @DisplayName("Marking unknown notification id is a no-op (200 OK)")
    void markRead_unknownId_isNoOp() throws Exception {
        // Method is idempotent: missing id is treated as already-handled
        mockMvc.perform(post("/api/notifications/000000000000000000000000/read")
                        .header("Authorization", bearer(athleteToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Notification endpoints require auth")
    void notificationEndpoints_requireAuth() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Custom page/size pagination params are echoed")
    void notifications_customPaging() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(athleteToken))
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));
    }
}
