package com.koval.trainingplannerbackend;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for club session capacity and waiting-list handling
 * (SessionParticipationService):
 * - Joining a full session lands on the waiting list
 * - Cancelling a participant promotes the first waitlisted user (FIFO)
 * - Promotion sends an in-app notification to the promoted user
 * - Cancelling from the waiting list removes the entry without promotion
 * - Joining is idempotent for participants and waitlisted users
 * - Cancelled sessions cannot be joined
 */
class ClubSessionWaitingListIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String member1Token;
    private String member2Token;
    private String member3Token;
    private String clubId;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        ownerToken = loginCoach("owner1");
        member1Token = loginAthlete("member1");
        member2Token = loginAthlete("member2");
        member3Token = loginAthlete("member3");

        clubId = createClub();
        joinClub(member1Token);
        joinClub(member2Token);
        joinClub(member3Token);
    }

    @Test
    @DisplayName("Joining a full session adds the user to the waiting list")
    void joinSession_givenCapacityReached_addsToWaitingList() throws Exception {
        String sessionId = createSession(1);

        joinSession(member1Token, sessionId)
                .andExpect(jsonPath("$.participantIds", hasItem("member1")));

        joinSession(member2Token, sessionId)
                .andExpect(jsonPath("$.participantIds", not(hasItem("member2"))))
                .andExpect(jsonPath("$.waitingList[*].userId", hasItem("member2")));
    }

    @Test
    @DisplayName("Cancelling a participant promotes the first waitlisted user in FIFO order")
    void cancelParticipation_givenWaitingList_promotesFirstInLine() throws Exception {
        String sessionId = createSession(1);

        joinSession(member1Token, sessionId);
        joinSession(member2Token, sessionId);
        joinSession(member3Token, sessionId);

        mockMvc.perform(delete("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(member1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds", hasItem("member2")))
                .andExpect(jsonPath("$.participantIds", not(hasItem("member3"))))
                .andExpect(jsonPath("$.waitingList[*].userId", hasItem("member3")))
                .andExpect(jsonPath("$.waitingList[*].userId", not(hasItem("member2"))));
    }

    @Test
    @DisplayName("Promoted user receives an in-app waiting-list-promoted notification")
    void cancelParticipation_givenPromotion_notifiesPromotedUser() throws Exception {
        String sessionId = createSession(1);

        joinSession(member1Token, sessionId);
        joinSession(member2Token, sessionId);

        mockMvc.perform(delete("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(member1Token)))
                .andExpect(status().isOk());

        // Notification dispatch is @Async — poll until it is persisted. Other club
        // notifications (e.g. session created) may exist, so filter by type.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mockMvc.perform(get("/api/notifications")
                                .header("Authorization", bearer(member2Token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.notifications[?(@.type=='WAITING_LIST_PROMOTED')]", hasSize(1)))
                        .andExpect(jsonPath("$.notifications[?(@.type=='WAITING_LIST_PROMOTED')].data.sessionId",
                                hasItem(sessionId))));
    }

    @Test
    @DisplayName("Cancelling from the waiting list removes the entry without promoting anyone")
    void cancelParticipation_givenUserOnWaitingList_removesWithoutPromotion() throws Exception {
        String sessionId = createSession(1);

        joinSession(member1Token, sessionId);
        joinSession(member2Token, sessionId);

        mockMvc.perform(delete("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(member2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds", hasItem("member1")))
                .andExpect(jsonPath("$.waitingList", hasSize(0)));
    }

    @Test
    @DisplayName("Joining twice is idempotent for participants and waitlisted users")
    void joinSession_givenAlreadyJoined_isIdempotent() throws Exception {
        String sessionId = createSession(1);

        joinSession(member1Token, sessionId);
        joinSession(member1Token, sessionId)
                .andExpect(jsonPath("$.participantIds", hasSize(1)))
                .andExpect(jsonPath("$.waitingList", hasSize(0)));

        joinSession(member2Token, sessionId);
        joinSession(member2Token, sessionId)
                .andExpect(jsonPath("$.participantIds", hasSize(1)))
                .andExpect(jsonPath("$.waitingList", hasSize(1)));
    }

    @Test
    @DisplayName("Joining a cancelled session is rejected")
    void joinSession_givenCancelledSession_returnsBadRequest() throws Exception {
        String sessionId = createSession(5);

        mockMvc.perform(put("/api/clubs/" + clubId + "/sessions/" + sessionId + "/cancel")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "Bad weather"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(member1Token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Session without capacity never builds a waiting list")
    void joinSession_givenNoCapacityLimit_neverWaitlists() throws Exception {
        String sessionId = createSession(null);

        joinSession(member1Token, sessionId);
        joinSession(member2Token, sessionId);
        joinSession(member3Token, sessionId)
                .andExpect(jsonPath("$.participantIds", hasSize(3)))
                .andExpect(jsonPath("$.waitingList", hasSize(0)));
    }

    // ---------- helpers ----------

    private String createClub() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Waitlist Club",
                                    "description": "Capacity testing",
                                    "visibility": "PUBLIC"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void joinClub(String token) throws Exception {
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private String createSession(Integer maxParticipants) throws Exception {
        LocalDateTime sessionTime = LocalDateTime.now().plusDays(1).withHour(18).withMinute(0);
        String maxField = maxParticipants != null
                ? ", \"maxParticipants\": " + maxParticipants
                : "";
        MvcResult result = mockMvc.perform(post("/api/clubs/" + clubId + "/sessions")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "title": "Capacity Ride",
                                    "sport": "CYCLING",
                                    "scheduledAt": "%s"%s
                                }
                                """.formatted(sessionTime, maxField)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions joinSession(String token, String sessionId) throws Exception {
        return mockMvc.perform(post("/api/clubs/" + clubId + "/sessions/" + sessionId + "/join")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
