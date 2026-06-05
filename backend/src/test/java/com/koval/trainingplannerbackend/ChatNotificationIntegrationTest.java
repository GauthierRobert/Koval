package com.koval.trainingplannerbackend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for chat push notifications: posting a chat message must
 * create an in-app notification for the other room members, excluding the
 * sender and members who muted the room. Notification dispatch is async, so
 * assertions poll briefly.
 */
class ChatNotificationIntegrationTest extends BaseIntegrationTest {

    private String coachToken;
    private String memberToken;
    private String mutedMemberToken;
    private String clubId;
    private String clubRoomId;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        coachToken = loginCoach("coach1");
        memberToken = loginAthlete("member1");
        mutedMemberToken = loginAthlete("member2");

        clubId = createClubAndJoin();
        clubRoomId = ensureClubRoom(coachToken);
        ensureClubRoom(memberToken);
        ensureClubRoom(mutedMemberToken);
    }

    @Test
    @DisplayName("Club chat message notifies other members but not the sender")
    void clubMessage_notifiesOtherMembers_notSender() throws Exception {
        postMessage(coachToken, clubRoomId, "Ride this Sunday at 9am?");

        JsonNode notification = awaitFirstNotification(memberToken);
        assertThat(notification.get("type").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(notification.get("body").asText()).contains("Ride this Sunday at 9am?");
        assertThat(notification.get("data").get("roomId").asText()).isEqualTo(clubRoomId);
        assertThat(notification.get("data").get("clubId").asText()).isEqualTo(clubId);

        // Sender must not be notified about their own message.
        assertThat(countNotifications(coachToken)).isZero();
    }

    @Test
    @DisplayName("Members who muted the room receive no notification")
    void clubMessage_mutedMember_isNotNotified() throws Exception {
        mockMvc.perform(post("/api/chat/rooms/" + clubRoomId + "/mute")
                        .header("Authorization", bearer(mutedMemberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"muted": true}
                                """))
                .andExpect(status().isNoContent());

        postMessage(coachToken, clubRoomId, "Hello everyone");

        // Wait until the unmuted member got their notification — dispatch happens
        // in a single async batch, so the muted member's state is settled by then.
        awaitFirstNotification(memberToken);
        assertThat(countNotifications(mutedMemberToken)).isZero();
    }

    @Test
    @DisplayName("Direct message notification carries the sender display name as title")
    void directMessage_titleIsSenderName() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/chat/rooms/direct")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"otherUserId": "member1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String dmRoomId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        postMessage(coachToken, dmRoomId, "Nice ride today!");

        JsonNode notification = awaitFirstNotification(memberToken);
        assertThat(notification.get("type").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(notification.get("title").asText()).isEqualTo("coach1-name");
        assertThat(notification.get("body").asText()).isEqualTo("Nice ride today!");
    }

    // --- Helpers ---

    private String createClubAndJoin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Chat Club","description":"d",
                                 "location":"Paris","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/clubs/" + id + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/clubs/" + id + "/join")
                        .header("Authorization", bearer(mutedMemberToken)))
                .andExpect(status().isOk());
        return id;
    }

    /** Ensures the club room exists and the caller has an active membership. */
    private String ensureClubRoom(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/chat/rooms/by-parent")
                        .header("Authorization", bearer(token))
                        .param("scope", "CLUB")
                        .param("clubId", clubId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void postMessage(String token, String roomId, String content) throws Exception {
        mockMvc.perform(post("/api/chat/rooms/" + roomId + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("content", content))))
                .andExpect(status().isOk());
    }

    /** Polls the notification center until the first notification arrives (async dispatch). */
    private JsonNode awaitFirstNotification(String token) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult res = mockMvc.perform(get("/api/notifications")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode notifications = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("notifications");
            if (notifications != null && !notifications.isEmpty()) {
                return notifications.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No notification arrived within 5s");
    }

    private long countNotifications(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("notifications").size();
    }
}
