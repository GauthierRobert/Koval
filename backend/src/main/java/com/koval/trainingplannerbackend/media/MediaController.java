package com.koval.trainingplannerbackend.media;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.media.dto.ConfirmUploadResponse;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;
import com.koval.trainingplannerbackend.media.dto.RequestUploadUrlRequest;
import com.koval.trainingplannerbackend.media.dto.RequestUploadUrlResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<RequestUploadUrlResponse> requestUploadUrl(
            @RequestBody RequestUploadUrlRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(mediaService.requestUploadUrl(userId, req));
    }

    @PostMapping("/{mediaId}/confirm")
    public ResponseEntity<ConfirmUploadResponse> confirm(@PathVariable String mediaId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(mediaService.confirmUpload(userId, mediaId));
    }

    @GetMapping("/{mediaId}/url")
    public ResponseEntity<MediaResponse> getReadUrl(@PathVariable String mediaId) {
        // Authorization on read is intentionally permissive at the media-service level:
        // anyone with the mediaId who is authenticated can fetch a signed URL.
        // Access control happens at the *consuming* feature level (gazette post, feed
        // event, avatar), where the parent resource enforces club membership before
        // exposing the mediaId in the first place.
        SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(mediaService.getReadResponse(mediaId));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> delete(@PathVariable String mediaId) {
        String userId = SecurityUtils.getCurrentUserId();
        mediaService.delete(userId, mediaId, false);
        return ResponseEntity.noContent().build();
    }
}
