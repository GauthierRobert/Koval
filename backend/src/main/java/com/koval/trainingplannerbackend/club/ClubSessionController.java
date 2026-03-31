package com.koval.trainingplannerbackend.club;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.club.dto.CancelSessionRequest;
import com.koval.trainingplannerbackend.club.dto.CreateSessionRequest;
import com.koval.trainingplannerbackend.club.dto.LinkTrainingRequest;
import com.koval.trainingplannerbackend.club.dto.UnlinkTrainingRequest;
import com.koval.trainingplannerbackend.club.session.ClubSessionService;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.SessionCategory;
import com.koval.trainingplannerbackend.club.session.SessionGpxService;
import com.koval.trainingplannerbackend.club.session.SessionParticipationService;
import com.koval.trainingplannerbackend.club.session.SessionTrainingLinkService;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubSessionController {

    private final ClubSessionService clubSessionService;
    private final SessionParticipationService sessionParticipationService;
    private final SessionGpxService sessionGpxService;
    private final SessionTrainingLinkService sessionTrainingLinkService;

    public ClubSessionController(ClubSessionService clubSessionService,
                                  SessionParticipationService sessionParticipationService,
                                  SessionGpxService sessionGpxService,
                                  SessionTrainingLinkService sessionTrainingLinkService) {
        this.clubSessionService = clubSessionService;
        this.sessionParticipationService = sessionParticipationService;
        this.sessionGpxService = sessionGpxService;
        this.sessionTrainingLinkService = sessionTrainingLinkService;
    }

    @PostMapping("/{id}/sessions")
    public ResponseEntity<ClubTrainingSession> createSession(@PathVariable String id,
                                                              @RequestBody CreateSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.createSession(userId, id, req));
    }

    @GetMapping("/{id}/sessions")
    public ResponseEntity<List<ClubTrainingSession>> listSessions(
            @PathVariable String id,
            @RequestParam(required = false) SessionCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        String userId = SecurityUtils.getCurrentUserId();
        if (from != null && to != null) {
            return ResponseEntity.ok(clubSessionService.listSessions(userId, id, category, from, to));
        }
        return ResponseEntity.ok(clubSessionService.listSessions(userId, id, category));
    }

    @PutMapping("/{id}/sessions/{sessionId}")
    public ResponseEntity<ClubTrainingSession> updateSession(@PathVariable String id,
                                                              @PathVariable String sessionId,
                                                              @RequestBody CreateSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.updateSession(userId, id, sessionId, req));
    }

    @PutMapping("/{id}/sessions/{sessionId}/cancel")
    public ResponseEntity<ClubTrainingSession> cancelEntireSession(@PathVariable String id,
                                                                     @PathVariable String sessionId,
                                                                     @RequestBody CancelSessionRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(clubSessionService.cancelEntireSession(userId, id, sessionId, req.reason()));
    }

    @PostMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> joinSession(@PathVariable String id,
                                                            @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionParticipationService.joinSession(userId, sessionId));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/join")
    public ResponseEntity<ClubTrainingSession> cancelSessionParticipation(@PathVariable String id,
                                                                           @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionParticipationService.cancelSessionParticipation(userId, sessionId));
    }

    // --- Link Training ---

    @PutMapping("/{id}/sessions/{sessionId}/link-training")
    public ResponseEntity<ClubTrainingSession> linkTrainingToSession(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestBody LinkTrainingRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionTrainingLinkService.linkTrainingToSession(userId, id, sessionId, req.trainingId(), req.clubGroupId()));
    }

    @PutMapping("/{id}/sessions/{sessionId}/unlink-training")
    public ResponseEntity<ClubTrainingSession> unlinkTrainingFromSession(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestBody UnlinkTrainingRequest req) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionTrainingLinkService.unlinkTrainingFromSession(userId, id, sessionId, req.clubGroupId()));
    }

    // --- GPX ---

    @PostMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<ClubTrainingSession> uploadSessionGpx(
            @PathVariable String id, @PathVariable String sessionId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionGpxService.uploadGpx(userId, id, sessionId, file));
    }

    @DeleteMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<ClubTrainingSession> deleteSessionGpx(
            @PathVariable String id, @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionGpxService.deleteGpx(userId, id, sessionId));
    }

    @GetMapping("/{id}/sessions/{sessionId}/gpx")
    public ResponseEntity<byte[]> downloadSessionGpx(
            @PathVariable String id, @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionGpxService.getGpxDownload(userId, id, sessionId);
    }
}
