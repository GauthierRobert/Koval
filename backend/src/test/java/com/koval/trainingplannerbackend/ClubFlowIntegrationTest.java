package com.koval.trainingplannerbackend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the club workflow:
 * 1. User creates a club
 * 2. Club appears in user's club list and public clubs
 * 3. Second user joins the club
 * 4. Owner approves membership (for private clubs)
 * 5. Members list, roles management
 * 6. Club groups
 * 7. Club training sessions (create, join, leave)
 * 8. Club stats, leaderboard, activity feed
 * 9. Recurring sessions
 * 10. Leave club
 */
class ClubFlowIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String memberToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        ownerToken = loginCoach("owner1");
        memberToken = loginAthlete("member1");
    }

    @Test
    @DisplayName("Create a public club and see it in lists")
    void createPublicClub() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Velocity Cycling Club",
                                    "description": "A club for fast riders",
                                    "location": "Paris",
                                    "visibility": "PUBLIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Velocity Cycling Club"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.ownerId").value("owner1"))
                .andReturn();

        // Owner sees club in their list
        mockMvc.perform(get("/api/clubs")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Velocity Cycling Club"));

        // Club appears in public browse
        mockMvc.perform(get("/api/clubs/public")
                        .header("Authorization", bearer(memberToken))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Full join flow: member joins public club, appears in member list")
    void joinPublicClub() throws Exception {
        String clubId = createClub("Open Club", "PUBLIC");

        // Member joins
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("member1"));

        // Member appears in club's member list
        mockMvc.perform(get("/api/clubs/" + clubId + "/members")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))); // owner + member
    }

    @Test
    @DisplayName("Private club: join creates pending request, owner approves")
    void privateClubApprovalFlow() throws Exception {
        String clubId = createClub("Exclusive Club", "PRIVATE");

        // Member requests to join
        MvcResult joinResult = mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String membershipId = objectMapper.readTree(
                joinResult.getResponse().getContentAsString()).get("id").asText();

        // Owner sees pending request
        mockMvc.perform(get("/api/clubs/" + clubId + "/members/pending")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Owner approves
        mockMvc.perform(post("/api/clubs/" + clubId + "/members/" + membershipId + "/approve")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // No more pending
        mockMvc.perform(get("/api/clubs/" + clubId + "/members/pending")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Owner rejects membership request")
    void rejectMembershipRequest() throws Exception {
        String clubId = createClub("Picky Club", "PRIVATE");

        MvcResult joinResult = mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andReturn();

        String membershipId = objectMapper.readTree(
                joinResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/clubs/" + clubId + "/members/" + membershipId + "/reject")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Get club detail shows full info")
    void getClubDetail() throws Exception {
        String clubId = createClub("Detail Club", "PUBLIC");

        mockMvc.perform(get("/api/clubs/" + clubId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Detail Club"))
                .andExpect(jsonPath("$.memberCount").isNumber());
    }

    @Test
    @DisplayName("Club groups: create, add member, remove member, delete")
    void clubGroupManagement() throws Exception {
        String clubId = createClub("Group Club", "PUBLIC");

        // Member joins
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());

        // Create group
        MvcResult groupResult = mockMvc.perform(post("/api/clubs/" + clubId + "/groups")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sprint Group"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sprint Group"))
                .andReturn();

        String groupId = objectMapper.readTree(
                groupResult.getResponse().getContentAsString()).get("id").asText();

        // List groups
        mockMvc.perform(get("/api/clubs/" + clubId + "/groups")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$", hasSize(1)));

        // Add member to group
        mockMvc.perform(post("/api/clubs/" + clubId + "/groups/" + groupId + "/members/member1")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // Remove member from group
        mockMvc.perform(delete("/api/clubs/" + clubId + "/groups/" + groupId + "/members/member1")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        // Delete group
        mockMvc.perform(delete("/api/clubs/" + clubId + "/groups/" + groupId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Club training session: create, join, cancel participation")
    void clubTrainingSession() throws Exception {
        String clubId = createClub("Session Club", "PUBLIC");

        // Member joins club
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());

        // Owner creates a session
        LocalDateTime sessionTime = LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);
        MvcResult sessionResult = mockMvc.perform(post("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Group Ride",
                                    "sport": "CYCLING",
                                    "scheduledAt": "%s",
                                    "location": "City Park",
                                    "description": "Easy group ride",
                                    "maxParticipants": 20
                                }
                                """.formatted(sessionTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Group Ride"))
                .andReturn();

        String sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("id").asText();

        // List sessions
        mockMvc.perform(get("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Member joins session
        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds", hasItem("member1")));

        // Member cancels participation
        mockMvc.perform(delete("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds", not(hasItem("member1"))));
    }

    @Test
    @DisplayName("Link training to club session")
    void linkTrainingToSession() throws Exception {
        String clubId = createClub("Link Club", "PUBLIC");

        // Create a training
        MvcResult trainingResult = mockMvc.perform(post("/api/trainings")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Club Workout",
                                    "sportType": "CYCLING",
                                    "trainingType": "ENDURANCE",
                                    "blocks": [{"type": "STEADY", "durationSeconds": 3600, "label": "Z2", "intensityTarget": 65}]
                                }
                                """))
                .andReturn();

        String trainingId = objectMapper.readTree(
                trainingResult.getResponse().getContentAsString()).get("id").asText();

        // Create session
        MvcResult sessionResult = mockMvc.perform(post("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Linked Session",
                                    "sport": "CYCLING",
                                    "scheduledAt": "%s"
                                }
                                """.formatted(LocalDateTime.now().plusDays(2))))
                .andReturn();

        String sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("id").asText();

        // Link training
        mockMvc.perform(put("/api/clubs/" + clubId + "/sessions/" + sessionId + "/link-training")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"trainingId": "%s"}
                                """.formatted(trainingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedTrainingId").value(trainingId));
    }

    @Test
    @DisplayName("Club weekly stats and leaderboard")
    void clubStatsAndLeaderboard() throws Exception {
        String clubId = createClub("Stats Club", "PUBLIC");

        mockMvc.perform(get("/api/clubs/" + clubId + "/stats/weekly")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clubs/" + clubId + "/leaderboard")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Club activity feed")
    void clubActivityFeed() throws Exception {
        String clubId = createClub("Feed Club", "PUBLIC");

        mockMvc.perform(get("/api/clubs/" + clubId + "/feed")
                        .header("Authorization", bearer(ownerToken))
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Update member role")
    void updateMemberRole() throws Exception {
        String clubId = createClub("Role Club", "PUBLIC");

        // Member joins
        MvcResult joinResult = mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andReturn();

        String membershipId = objectMapper.readTree(
                joinResult.getResponse().getContentAsString()).get("id").asText();

        // Owner promotes to COACH
        mockMvc.perform(put("/api/clubs/" + clubId + "/members/" + membershipId + "/role")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "COACH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COACH"));
    }

    @Test
    @DisplayName("Member leaves club")
    void leaveClub() throws Exception {
        String clubId = createClub("Leave Club", "PUBLIC");

        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/clubs/" + clubId + "/leave")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isNoContent());

        // Member no longer sees club
        mockMvc.perform(get("/api/clubs")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Get user's club roles")
    void getMyClubRoles() throws Exception {
        String clubId = createClub("Roles Club", "PUBLIC");

        mockMvc.perform(get("/api/clubs/my-roles")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    @DisplayName("Recurring sessions: create, list, update, deactivate")
    void recurringSessionsCrud() throws Exception {
        String clubId = createClub("Recurring Club", "PUBLIC");

        // Create recurring session
        MvcResult result = mockMvc.perform(post("/api/clubs/" + clubId + "/recurring-sessions")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Tuesday Group Ride",
                                    "sport": "CYCLING",
                                    "dayOfWeek": "TUESDAY",
                                    "timeOfDay": "18:00",
                                    "location": "Town Square",
                                    "description": "Weekly group ride"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tuesday Group Ride"))
                .andReturn();

        String templateId = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("id").asText();

        // List recurring sessions
        mockMvc.perform(get("/api/clubs/" + clubId + "/recurring-sessions")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$", hasSize(1)));

        // Update
        mockMvc.perform(put("/api/clubs/" + clubId + "/recurring-sessions/" + templateId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Updated Tuesday Ride",
                                    "sport": "CYCLING",
                                    "dayOfWeek": "TUESDAY",
                                    "timeOfDay": "19:00",
                                    "location": "New Location"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Tuesday Ride"));

        // Deactivate
        mockMvc.perform(delete("/api/clubs/" + clubId + "/recurring-sessions/" + templateId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Club race goals endpoint")
    void clubRaceGoals() throws Exception {
        String clubId = createClub("Goals Club", "PUBLIC");

        mockMvc.perform(get("/api/clubs/" + clubId + "/race-goals")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- Helper ---

    private String createClub(String name, String visibility) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s", "description": "Test club", "location": "Test City", "visibility": "%s"}
                                """.formatted(name, visibility)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
