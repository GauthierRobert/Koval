package com.koval.trainingplannerbackend;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEvent;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventRepository;
import com.koval.trainingplannerbackend.club.feed.ClubFeedEventType;
import com.koval.trainingplannerbackend.club.feed.ClubFeedSpotlightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the coach engagement toolkit:
 * reactions, threaded replies, mentions, member spotlight, engagement insights.
 */
class ClubEngagementIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ClubFeedEventRepository feedEventRepository;

    @Autowired
    private ClubFeedSpotlightService spotlightService;

    private String coachToken;
    private String memberToken;
    private String otherToken;

    @BeforeEach
    void setup() throws Exception {
        super.cleanDatabase();
        coachToken = loginCoach("coach1");
        memberToken = loginAthlete("member1");
        otherToken = loginAthlete("member2");
    }

    @Test
    @DisplayName("Reactions toggle on a feed event and broadcast counts")
    void reactionsToggle() throws Exception {
        String clubId = createClubAndJoin();
        String eventId = postAnnouncement(clubId, "Hello team");

        // Member reacts with 'fire' — count goes from 0 to 1
        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId + "/reactions")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"fire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emoji").value("fire"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.userReacted").value(true));

        // Same member toggles again — removes
        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId + "/reactions")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"fire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.userReacted").value(false));
    }

    @Test
    @DisplayName("Reactions reject unknown emoji")
    void reactionsRejectUnknownEmoji() throws Exception {
        String clubId = createClubAndJoin();
        String eventId = postAnnouncement(clubId, "Hi");

        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId + "/reactions")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"poop\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Threaded reply attaches a parentCommentId; second-level replies rejected")
    void threadedReply() throws Exception {
        String clubId = createClubAndJoin();
        String eventId = postAnnouncement(clubId, "Discuss this");

        // Member adds top-level comment
        MvcResult cm = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId + "/comments")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"First\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String commentId = objectMapper.readTree(cm.getResponse().getContentAsString())
                .get("id").asText();

        // Coach replies under the comment
        MvcResult rp = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId
                                + "/comments/" + commentId + "/replies")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Reply!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentCommentId").value(commentId))
                .andReturn();
        String replyId = objectMapper.readTree(rp.getResponse().getContentAsString())
                .get("id").asText();

        // Reply-on-reply rejected (single-level)
        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId
                                + "/comments/" + replyId + "/replies")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Nope\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Mentions in announcement persist mentionRefs for valid active members and drop forged IDs")
    void mentionsPersistedAndForgedDropped() throws Exception {
        String clubId = createClubAndJoin();

        MvcResult res = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/announcements")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Welcome @member1 and @ghost",
                                 "mediaIds":[],
                                 "mentionUserIds":["member1","not-a-member-xyz"]}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode refs = body.get("mentionRefs");
        assertThat(refs.isArray()).isTrue();
        assertThat(refs.size()).isEqualTo(1);
        assertThat(refs.get(0).get("userId").asText()).isEqualTo("member1");
    }

    @Test
    @DisplayName("Mention suggest endpoint returns active members filtered by query")
    void mentionSuggest() throws Exception {
        String clubId = createClubAndJoin();

        mockMvc.perform(get("/api/clubs/" + clubId + "/feed/mentions/suggest")
                        .header("Authorization", bearer(coachToken))
                        .param("q", "member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)); // member1 + member2 -name suffixes match
    }

    @Test
    @DisplayName("Member cannot create a spotlight; coach can; spotlight is pinned")
    void spotlightCreate() throws Exception {
        String clubId = createClubAndJoin();

        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/spotlights")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spotlightedUserId":"member1","title":"Self promo",
                                 "message":"hi","badge":"PR","mediaIds":[],"expiresInDays":7,
                                 "mentionUserIds":[]}
                                """))
                .andExpect(status().is4xxClientError());

        MvcResult ok = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/spotlights")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spotlightedUserId":"member1","title":"Sub-3 marathon!",
                                 "message":"Great work","badge":"PR","mediaIds":[],
                                 "expiresInDays":7,"mentionUserIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MEMBER_SPOTLIGHT"))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.spotlightedUserId").value("member1"))
                .andExpect(jsonPath("$.spotlightBadge").value("PR"))
                .andReturn();

        assertThat(objectMapper.readTree(ok.getResponse().getContentAsString())
                .get("spotlightExpiresAt").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("Spotlight expiry scheduler unpins past spotlights")
    void spotlightExpiryUnpins() throws Exception {
        String clubId = createClubAndJoin();

        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/spotlights")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spotlightedUserId":"member1","title":"Quick",
                                 "message":"x","badge":"GRIT","mediaIds":[],
                                 "expiresInDays":1,"mentionUserIds":[]}
                                """))
                .andExpect(status().isOk());

        // Force expiry: rewind expiresAt into the past, then run the unpin pass.
        List<ClubFeedEvent> spotlights = feedEventRepository
                .findByClubIdAndTypeOrderByCreatedAtDesc(clubId,
                        ClubFeedEventType.MEMBER_SPOTLIGHT,
                        org.springframework.data.domain.PageRequest.of(0, 5));
        assertThat(spotlights).hasSize(1);
        ClubFeedEvent ev = spotlights.get(0);
        ev.setSpotlightExpiresAt(LocalDateTime.now().minusHours(1));
        feedEventRepository.save(ev);

        int unpinned = spotlightService.unpinExpiredSpotlights(LocalDateTime.now());
        assertThat(unpinned).isEqualTo(1);

        ClubFeedEvent reloaded = feedEventRepository.findById(ev.getId()).orElseThrow();
        assertThat(reloaded.getPinned()).isFalse();
    }

    @Test
    @DisplayName("Engagement insights are coach/admin only and return per-member rows")
    void engagementInsightsCoachOnly() throws Exception {
        String clubId = createClubAndJoin();

        // Member 403
        mockMvc.perform(get("/api/clubs/" + clubId + "/feed/engagement-insights")
                        .header("Authorization", bearer(memberToken))
                        .param("days", "30"))
                .andExpect(status().is4xxClientError());

        // Coach 200 with a row per active member (coach + 2 athletes = 3)
        mockMvc.perform(get("/api/clubs/" + clubId + "/feed/engagement-insights")
                        .header("Authorization", bearer(coachToken))
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.members.length()").value(3));
    }

    @Test
    @DisplayName("Comment reaction toggles emoji set on the embedded comment")
    void commentReactionToggle() throws Exception {
        String clubId = createClubAndJoin();
        String eventId = postAnnouncement(clubId, "react under me");

        MvcResult cm = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId + "/comments")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"first\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String commentId = objectMapper.readTree(cm.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/clubs/" + clubId + "/feed/" + eventId
                                + "/comments/" + commentId + "/reactions")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"clap\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(commentId))
                .andExpect(jsonPath("$.emoji").value("clap"))
                .andExpect(jsonPath("$.count").value(1));
    }

    // --- Helpers ---

    private String createClubAndJoin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/clubs")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Engagement Club","description":"d",
                                 "location":"Paris","visibility":"PUBLIC"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String clubId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/clubs/" + clubId + "/join")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk());
        return clubId;
    }

    private String postAnnouncement(String clubId, String content) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/clubs/" + clubId + "/feed/announcements")
                        .header("Authorization", bearer(coachToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\",\"mediaIds\":[]}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @SuppressWarnings("unused")
    private void deleteSpotlight(String clubId, String eventId) throws Exception {
        mockMvc.perform(delete("/api/clubs/" + clubId + "/feed/spotlights/" + eventId)
                        .header("Authorization", bearer(coachToken)))
                .andExpect(status().isNoContent());
    }
}
