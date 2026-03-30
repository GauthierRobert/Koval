package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the authentication flow:
 * - Dev login (create user)
 * - Get current user
 * - Update settings
 * - Complete onboarding
 * - Role management
 */
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Dev login creates a new user and returns JWT + user info")
    void devLogin_createsUser() throws Exception {
        mockMvc.perform(post("/api/auth/dev/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "user1", "displayName": "Alice", "role": "ATHLETE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value("user1"))
                .andExpect(jsonPath("$.user.displayName").value("Alice"))
                .andExpect(jsonPath("$.user.role").value("ATHLETE"));
    }

    @Test
    @DisplayName("Dev login with blank userId returns 400")
    void devLogin_blankUserId_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/dev/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "", "displayName": "Alice"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Dev login returns the same user on second call")
    void devLogin_existingUser_returnsSame() throws Exception {
        String token1 = loginUser("user1", "Alice", "ATHLETE");
        String token2 = loginUser("user1", "Alice", "ATHLETE");

        // Both tokens should decode to the same user
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user1"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns current user info")
    void getMe_returnsCurrentUser() throws Exception {
        String token = loginUser("user1", "Alice", "COACH");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user1"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.role").value("COACH"));
    }

    @Test
    @DisplayName("GET /api/auth/me without token returns 401")
    void getMe_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/auth/settings updates user FTP and weight")
    void updateSettings_updatesFtpAndWeight() throws Exception {
        String token = loginAthlete("user1");

        mockMvc.perform(put("/api/auth/settings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ftp": 280, "weightKg": 75}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ftp").value(280))
                .andExpect(jsonPath("$.weightKg").value(75));

        // Verify persisted
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ftp").value(280))
                .andExpect(jsonPath("$.weightKg").value(75));
    }

    @Test
    @DisplayName("PUT /api/auth/settings updates running paces")
    void updateSettings_updatesRunningPaces() throws Exception {
        String token = loginAthlete("runner1");

        mockMvc.perform(put("/api/auth/settings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"functionalThresholdPace": 270, "pace5k": 240, "pace10k": 255}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.functionalThresholdPace").value(270))
                .andExpect(jsonPath("$.pace5k").value(240))
                .andExpect(jsonPath("$.pace10k").value(255));
    }

    @Test
    @DisplayName("POST /api/auth/onboarding completes onboarding and re-issues JWT")
    void completeOnboarding_updatesRoleAndSettings() throws Exception {
        String token = loginAthlete("newbie");

        mockMvc.perform(post("/api/auth/onboarding")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "COACH", "ftp": 300, "weightKg": 80}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("COACH"))
                .andExpect(jsonPath("$.user.ftp").value(300))
                .andExpect(jsonPath("$.user.weightKg").value(80));
    }

    @Test
    @DisplayName("POST /api/auth/role changes user role")
    void setRole_changesRole() throws Exception {
        String token = loginAthlete("user1");

        mockMvc.perform(post("/api/auth/role")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "COACH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"));

        // Verify persisted
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.role").value("COACH"));
    }

    @Test
    @DisplayName("Authenticated request with invalid token returns 403")
    void invalidToken_returns403() throws Exception {
        mockMvc.perform(get("/api/trainings")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint without auth returns 403")
    void noAuth_protectedEndpoint_returns403() throws Exception {
        mockMvc.perform(get("/api/trainings"))
                .andExpect(status().isForbidden());
    }
}
