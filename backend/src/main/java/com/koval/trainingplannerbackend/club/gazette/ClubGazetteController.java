package com.koval.trainingplannerbackend.club.gazette;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.gazette.dto.AddCommentRequest;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazetteEditionSummary;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePostResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.ClubGazettePostsResponse;
import com.koval.trainingplannerbackend.club.gazette.dto.CreateGazettePostRequest;
import com.koval.trainingplannerbackend.club.gazette.dto.UpdateGazettePostRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/gazette")
public class ClubGazetteController {

    private final ClubGazetteService gazetteService;

    public ClubGazetteController(ClubGazetteService gazetteService) {
        this.gazetteService = gazetteService;
    }

    // ── Editions ─────────────────────────────────────────────────────────────

    @GetMapping("/editions")
    public ResponseEntity<List<ClubGazetteEditionSummary>> listEditions(
            @PathVariable String clubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.listEditions(userId, clubId, page, size));
    }

    @GetMapping("/editions/{editionId}")
    public ResponseEntity<ClubGazetteEditionResponse> getEdition(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.getEdition(userId, clubId, editionId));
    }

    @GetMapping("/editions/current")
    public ResponseEntity<ClubGazetteEditionResponse> getCurrentDraft(@PathVariable String clubId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.getCurrentDraft(userId, clubId));
    }

    @GetMapping("/editions/drafts")
    public ResponseEntity<List<ClubGazetteEditionResponse>> getOpenDrafts(@PathVariable String clubId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.getOpenDrafts(userId, clubId));
    }

    @DeleteMapping("/editions/{editionId}")
    public ResponseEntity<Void> discardDraft(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        gazetteService.discardDraft(userId, editionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/editions/{editionId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        byte[] pdf = gazetteService.getPdfBytes(userId, clubId, editionId);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"gazette-" + editionId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/editions/{editionId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        gazetteService.markAsRead(userId, editionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/editions/{editionId}/comments")
    public ResponseEntity<ClubGazetteEdition.CommentEntry> addComment(
            @PathVariable String clubId,
            @PathVariable String editionId,
            @RequestBody AddCommentRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.addComment(userId, editionId, req.content()));
    }

    // ── Posts ────────────────────────────────────────────────────────────────

    @GetMapping("/editions/{editionId}/posts")
    public ResponseEntity<ClubGazettePostsResponse> listPosts(
            @PathVariable String clubId,
            @PathVariable String editionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.listPosts(userId, editionId));
    }

    @PostMapping("/editions/{editionId}/posts")
    public ResponseEntity<ClubGazettePostResponse> createPost(
            @PathVariable String clubId,
            @PathVariable String editionId,
            @RequestBody CreateGazettePostRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.createPost(userId, editionId, req));
    }

    @PatchMapping("/editions/{editionId}/posts/{postId}")
    public ResponseEntity<ClubGazettePostResponse> updatePost(
            @PathVariable String clubId,
            @PathVariable String editionId,
            @PathVariable String postId,
            @RequestBody UpdateGazettePostRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.updatePost(userId, postId, req));
    }

    @DeleteMapping("/editions/{editionId}/posts/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable String clubId,
            @PathVariable String editionId,
            @PathVariable String postId) {
        String userId = SecurityUtils.getCurrentUserId();
        gazetteService.deletePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/editions/current/posts/mine")
    public ResponseEntity<List<ClubGazettePostResponse>> myPostsInCurrentDraft(
            @PathVariable String clubId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(gazetteService.getMyPostsInCurrentDraft(userId, clubId));
    }
}
