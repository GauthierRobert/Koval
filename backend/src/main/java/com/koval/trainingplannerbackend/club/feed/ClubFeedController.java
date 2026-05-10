package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserService;
import com.koval.trainingplannerbackend.club.feed.dto.AddCommentRequest;
import com.koval.trainingplannerbackend.club.feed.dto.AttachPhotosRequest;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CreateAnnouncementRequest;
import com.koval.trainingplannerbackend.club.feed.dto.CreateSpotlightRequest;
import com.koval.trainingplannerbackend.club.feed.dto.EngagementInsightsResponse;
import com.koval.trainingplannerbackend.club.feed.dto.KudosResponse;
import com.koval.trainingplannerbackend.club.feed.dto.MentionSuggestion;
import com.koval.trainingplannerbackend.club.feed.dto.PhotoEnrichmentResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ReactionStateResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ToggleReactionRequest;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateAnnouncementRequest;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateCommentRequest;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateSpotlightRequest;
import com.koval.trainingplannerbackend.club.membership.ClubAuthorizationService;
import com.koval.trainingplannerbackend.club.membership.ClubMemberStatus;
import com.koval.trainingplannerbackend.club.membership.ClubMembership;
import com.koval.trainingplannerbackend.club.membership.ClubMembershipRepository;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clubs/{clubId}/feed")
public class ClubFeedController {

    private static final int MAX_MENTION_SUGGESTIONS = 8;

    private final ClubFeedService feedService;
    private final ClubFeedReactionService reactionService;
    private final ClubFeedSpotlightService spotlightService;
    private final ClubEngagementInsightsService insightsService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final StravaKudosService kudosService;
    private final ClubAuthorizationService authorizationService;
    private final ClubMembershipRepository membershipRepository;
    private final UserService userService;

    public ClubFeedController(ClubFeedService feedService,
                              ClubFeedReactionService reactionService,
                              ClubFeedSpotlightService spotlightService,
                              ClubEngagementInsightsService insightsService,
                              ClubFeedSseBroadcaster broadcaster,
                              StravaKudosService kudosService,
                              ClubAuthorizationService authorizationService,
                              ClubMembershipRepository membershipRepository,
                              UserService userService) {
        this.feedService = feedService;
        this.reactionService = reactionService;
        this.spotlightService = spotlightService;
        this.insightsService = insightsService;
        this.broadcaster = broadcaster;
        this.kudosService = kudosService;
        this.authorizationService = authorizationService;
        this.membershipRepository = membershipRepository;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<ClubFeedResponse> getFeed(
            @PathVariable String clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.getFeed(userId, clubId, page, size));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamFeed(@PathVariable String clubId) {
        // Auth is handled by the JWT filter on the HTTP request
        return broadcaster.register(clubId);
    }

    @PostMapping("/announcements")
    public ResponseEntity<ClubFeedEventResponse> createAnnouncement(
            @PathVariable String clubId,
            @Valid @RequestBody CreateAnnouncementRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                feedService.createCoachAnnouncement(userId, clubId, req.content(), req.mediaIds(),
                        req.mentionUserIds()));
    }

    @PutMapping("/announcements/{eventId}")
    public ResponseEntity<ClubFeedEventResponse> updateAnnouncement(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @Valid @RequestBody UpdateAnnouncementRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                feedService.updateCoachAnnouncement(userId, clubId, eventId, req.content(), req.mediaIds(),
                        req.mentionUserIds()));
    }

    @DeleteMapping("/announcements/{eventId}")
    public ResponseEntity<Void> deleteAnnouncement(
            @PathVariable String clubId,
            @PathVariable String eventId) {
        String userId = SecurityUtils.getCurrentUserId();
        feedService.deleteCoachAnnouncement(userId, clubId, eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/kudos")
    public ResponseEntity<KudosResponse> giveKudos(
            @PathVariable String clubId,
            @PathVariable String eventId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(kudosService.giveKudosToAll(userId, eventId));
    }

    @PostMapping("/{eventId}/reactions")
    public ResponseEntity<ReactionStateResponse> toggleEventReaction(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @Valid @RequestBody ToggleReactionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(reactionService.toggleEventReaction(userId, clubId, eventId, req.emoji()));
    }

    @PostMapping("/{eventId}/comments/{commentId}/reactions")
    public ResponseEntity<ReactionStateResponse> toggleCommentReaction(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String commentId,
            @Valid @RequestBody ToggleReactionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(reactionService.toggleCommentReaction(
                userId, clubId, eventId, commentId, req.emoji()));
    }

    @PostMapping("/{eventId}/comments")
    public ResponseEntity<ClubFeedEvent.CommentEntry> addComment(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @Valid @RequestBody AddCommentRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.addComment(
                userId, clubId, eventId, req.content(), null, req.mentionUserIds()));
    }

    @PostMapping("/{eventId}/comments/{parentCommentId}/replies")
    public ResponseEntity<ClubFeedEvent.CommentEntry> addReply(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String parentCommentId,
            @Valid @RequestBody AddCommentRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.addComment(
                userId, clubId, eventId, req.content(), parentCommentId, req.mentionUserIds()));
    }

    @PutMapping("/{eventId}/comments/{commentId}")
    public ResponseEntity<ClubFeedEvent.CommentEntry> updateComment(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String commentId,
            @Valid @RequestBody UpdateCommentRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.updateComment(userId, clubId, eventId, commentId, req.content()));
    }

    @DeleteMapping("/{eventId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String commentId) {
        String userId = SecurityUtils.getCurrentUserId();
        feedService.deleteComment(userId, clubId, eventId, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{eventId}/photos")
    public ResponseEntity<List<PhotoEnrichmentResponse>> attachPhotos(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @RequestBody AttachPhotosRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.attachPhotos(userId, clubId, eventId, req.mediaIds()));
    }

    @DeleteMapping("/{eventId}/photos/{enrichmentId}")
    public ResponseEntity<Void> detachPhoto(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String enrichmentId) {
        String userId = SecurityUtils.getCurrentUserId();
        feedService.detachPhoto(userId, clubId, eventId, enrichmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/spotlights")
    public ResponseEntity<ClubFeedEventResponse> createSpotlight(
            @PathVariable String clubId,
            @RequestBody CreateSpotlightRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(spotlightService.createSpotlight(userId, clubId, req));
    }

    @PutMapping("/spotlights/{eventId}")
    public ResponseEntity<ClubFeedEventResponse> updateSpotlight(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @RequestBody UpdateSpotlightRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(spotlightService.updateSpotlight(userId, clubId, eventId, req));
    }

    @DeleteMapping("/spotlights/{eventId}")
    public ResponseEntity<Void> deleteSpotlight(
            @PathVariable String clubId,
            @PathVariable String eventId) {
        String userId = SecurityUtils.getCurrentUserId();
        spotlightService.deleteSpotlight(userId, clubId, eventId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mentions/suggest")
    public ResponseEntity<List<MentionSuggestion>> suggestMentions(
            @PathVariable String clubId,
            @RequestParam(name = "q", required = false, defaultValue = "") String q) {
        String userId = SecurityUtils.getCurrentUserId();
        authorizationService.requireActiveMember(userId, clubId);

        List<ClubMembership> active = membershipRepository.findByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);
        List<String> ids = active.stream().map(ClubMembership::getUserId).toList();
        Map<String, ClubMembership> byId = active.stream()
                .collect(Collectors.toMap(ClubMembership::getUserId, m -> m, (a, b) -> a));

        String needle = q == null ? "" : q.trim().toLowerCase();
        return ResponseEntity.ok(userService.findAllById(ids).stream()
                .filter(u -> needle.isEmpty()
                        || (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(needle)))
                .sorted((a, b) -> safe(a.getDisplayName()).compareToIgnoreCase(safe(b.getDisplayName())))
                .limit(MAX_MENTION_SUGGESTIONS)
                .map(u -> {
                    ClubMembership m = byId.get(u.getId());
                    String role = m != null ? m.getRole().name() : "MEMBER";
                    return new MentionSuggestion(u.getId(), u.getDisplayName(), u.getProfilePicture(), role);
                })
                .toList());
    }

    @GetMapping("/engagement-insights")
    public ResponseEntity<EngagementInsightsResponse> engagementInsights(
            @PathVariable String clubId,
            @RequestParam(name = "days", required = false, defaultValue = "30") int days) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(insightsService.getInsights(userId, clubId, days));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
