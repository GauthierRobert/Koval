package com.koval.trainingplannerbackend.club.feed;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.feed.dto.AddCommentRequest;
import com.koval.trainingplannerbackend.club.feed.dto.AttachPhotosRequest;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedEventResponse;
import com.koval.trainingplannerbackend.club.feed.dto.ClubFeedResponse;
import com.koval.trainingplannerbackend.club.feed.dto.CreateAnnouncementRequest;
import com.koval.trainingplannerbackend.club.feed.dto.KudosResponse;
import com.koval.trainingplannerbackend.club.feed.dto.PhotoEnrichmentResponse;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateAnnouncementRequest;
import com.koval.trainingplannerbackend.club.feed.dto.UpdateCommentRequest;
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

@RestController
@RequestMapping("/api/clubs/{clubId}/feed")
public class ClubFeedController {

    private final ClubFeedService feedService;
    private final ClubFeedSseBroadcaster broadcaster;
    private final StravaKudosService kudosService;

    public ClubFeedController(ClubFeedService feedService,
                              ClubFeedSseBroadcaster broadcaster,
                              StravaKudosService kudosService) {
        this.feedService = feedService;
        this.broadcaster = broadcaster;
        this.kudosService = kudosService;
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
            @RequestBody CreateAnnouncementRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                feedService.createCoachAnnouncement(userId, clubId, req.content(), req.mediaIds()));
    }

    @PutMapping("/announcements/{eventId}")
    public ResponseEntity<ClubFeedEventResponse> updateAnnouncement(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @RequestBody UpdateAnnouncementRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                feedService.updateCoachAnnouncement(userId, clubId, eventId, req.content(), req.mediaIds()));
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

    @PostMapping("/{eventId}/comments")
    public ResponseEntity<ClubFeedEvent.CommentEntry> addComment(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @RequestBody AddCommentRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(feedService.addComment(userId, clubId, eventId, req.content()));
    }

    @PutMapping("/{eventId}/comments/{commentId}")
    public ResponseEntity<ClubFeedEvent.CommentEntry> updateComment(
            @PathVariable String clubId,
            @PathVariable String eventId,
            @PathVariable String commentId,
            @RequestBody UpdateCommentRequest req) {
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
}
