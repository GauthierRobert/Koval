package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final SessionService sessionService;
    private final AnalyticsService analyticsService;

    public SessionController(SessionService sessionService,
                             AnalyticsService analyticsService) {
        this.sessionService = sessionService;
        this.analyticsService = analyticsService;
    }

    @PostMapping
    public ResponseEntity<CompletedSession> save(@RequestBody CompletedSession session) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.saveSession(session, userId));
    }

    @GetMapping
    public ResponseEntity<List<CompletedSession>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listSessions(userId));
    }

    @GetMapping(params = "page")
    public ResponseEntity<Page<CompletedSession>> list(Pageable pageable) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listSessions(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompletedSession> getById(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionService.getSession(userId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CompletedSession>> listForCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listForCalendar(userId, start, end));
    }

    @PostMapping("/{sessionId}/link/{scheduledWorkoutId}")
    public ResponseEntity<CompletedSession> linkToSchedule(
            @PathVariable String sessionId,
            @PathVariable String scheduledWorkoutId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.linkSessionToSchedule(sessionId, scheduledWorkoutId, userId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CompletedSession> patch(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.patchSession(id, body, userId);
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionService.deleteSession(id, userId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ── PMC endpoint ─────────────────────────────────────────────────────────

    @GetMapping("/pmc")
    public ResponseEntity<List<AnalyticsService.PmcDataPoint>> getPmc(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.generatePmc(userId, from, to));
    }

    // ── FIT file upload / download ────────────────────────────────────────────

    @PostMapping("/{id}/fit")
    public ResponseEntity<CompletedSession> uploadFit(@PathVariable String id,
            @RequestParam("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.uploadFitFile(id, userId, file.getInputStream());
        return result != null ? ResponseEntity.ok(result) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/fit")
    public ResponseEntity<?> downloadFit(@PathVariable String id) throws IOException {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionService.downloadFitFile(id, userId)
                .map(fit -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + fit.filename() + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body((Object) fit.data()))
                .orElse(ResponseEntity.notFound().build());
    }
}
