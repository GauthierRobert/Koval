package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.race.Race;
import com.koval.trainingplannerbackend.training.metrics.PowerCurveService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** REST API for completed workout sessions: CRUD, FIT file management, analytics, and power curves. */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionHistoryQueryService historyQueryService;
    private final SessionFitFileService fitFileService;
    private final AnalyticsService analyticsService;
    private final PowerCurveService powerCurveService;
    private final SessionAlignmentService alignmentService;

    public SessionController(SessionService sessionService,
                             SessionHistoryQueryService historyQueryService,
                             SessionFitFileService fitFileService,
                             AnalyticsService analyticsService,
                             PowerCurveService powerCurveService,
                             SessionAlignmentService alignmentService) {
        this.sessionService = sessionService;
        this.historyQueryService = historyQueryService;
        this.fitFileService = fitFileService;
        this.analyticsService = analyticsService;
        this.powerCurveService = powerCurveService;
        this.alignmentService = alignmentService;
    }

    /** Saves a completed workout session with automatic metrics computation and schedule association. */
    @PostMapping
    public ResponseEntity<CompletedSession> save(@RequestBody CompletedSession session) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.saveSession(session, userId));
    }

    /** Lists all completed sessions for the authenticated user, most recent first. */
    @GetMapping
    public ResponseEntity<List<CompletedSession>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listSessions(userId));
    }

    /** Paginated variant of {@link #list()}. */
    @GetMapping(params = "page")
    public ResponseEntity<Page<CompletedSession>> list(Pageable pageable) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listSessions(userId, pageable));
    }

    /** Retrieves a single session by ID (accessible to the owner or their coach). */
    @GetMapping("/{id}")
    public ResponseEntity<CompletedSession> getById(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionService.getSession(userId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lists sessions within a date range for calendar display. */
    @GetMapping("/calendar")
    public ResponseEntity<List<CompletedSession>> listForCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listForCalendar(userId, start, end));
    }

    /**
     * Returns a Monday-aligned slice of completed sessions for paginated history browsing.
     * Filters are applied server-side. To page older, pass {@code before = result.windowStart}.
     */
    @GetMapping("/window")
    public ResponseEntity<SessionHistoryQueryService.SessionWindowResult> listWindow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(defaultValue = "8") int weeks,
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer durationMinSec,
            @RequestParam(required = false) Integer durationMaxSec,
            @RequestParam(required = false) Double tssMin,
            @RequestParam(required = false) Double tssMax) {
        String userId = SecurityUtils.getCurrentUserId();
        SessionHistoryQueryService.WindowFilters filters = new SessionHistoryQueryService.WindowFilters(
                sport, from, to, durationMinSec, durationMaxSec, tssMin, tssMax);
        return ResponseEntity.ok(historyQueryService.listWindow(userId, before, weeks, filters));
    }

    /** Manually links a completed session to a scheduled workout. */
    @PostMapping("/{sessionId}/link/{scheduledWorkoutId}")
    public ResponseEntity<CompletedSession> linkToSchedule(
            @PathVariable String sessionId,
            @PathVariable String scheduledWorkoutId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.linkSessionToSchedule(sessionId, scheduledWorkoutId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Ranked link candidates within ±3 days for the manual picker, with score breakdown. */
    @GetMapping("/{sessionId}/link-candidates")
    public ResponseEntity<List<SessionAssociationService.LinkCandidate>> listLinkCandidates(
            @PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listLinkCandidates(sessionId, userId));
    }

    /** Dismiss the pending auto-suggestion without committing to any link. */
    @PostMapping("/{sessionId}/dismiss-suggestion")
    public ResponseEntity<CompletedSession> dismissSuggestion(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.dismissSuggestion(sessionId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Mark the session as not part of any planned workout (suppresses future prompts). */
    @PostMapping("/{sessionId}/mark-unplanned")
    public ResponseEntity<CompletedSession> markUnplanned(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.markUnplanned(sessionId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Undo a session→scheduled-workout link; reverts the scheduled workout to PENDING. */
    @PostMapping("/{sessionId}/unlink-schedule")
    public ResponseEntity<CompletedSession> unlinkSchedule(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.unlinkFromSchedule(sessionId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Races (from the athlete's goals) whose scheduledDate matches this session's day. */
    @GetMapping("/{sessionId}/race-candidates")
    public ResponseEntity<List<Race>> listRaceCandidates(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(sessionService.listRaceCandidates(sessionId, userId));
    }

    /**
     * Classify a session against a race day:
     * {@code role=RACE} bundles it into the race chain; {@code WARMUP} marks it race-day but separate;
     * {@code NONE} just dismisses the prompt.
     */
    @PostMapping("/{sessionId}/classify-race")
    public ResponseEntity<CompletedSession> classifyRace(
            @PathVariable String sessionId,
            @RequestBody ClassifyRaceRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.classifyRace(sessionId, request.raceId(), request.role(), userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    public record ClassifyRaceRequest(String raceId, RaceRole role) {}

    /** Remove a session's race classification (raceId + role), reverting it to the unclassified state. */
    @PostMapping("/{sessionId}/unclassify-race")
    public ResponseEntity<CompletedSession> unclassifyRace(@PathVariable String sessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.unclassifyRace(sessionId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Links a completed session to a club training session. */
    @PostMapping("/{sessionId}/link-club-session/{clubSessionId}")
    public ResponseEntity<CompletedSession> linkToClubSession(
            @PathVariable String sessionId,
            @PathVariable String clubSessionId) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.linkSessionToClubSession(sessionId, clubSessionId, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Patches session fields (currently supports RPE with automatic TSS fallback). */
    @PatchMapping("/{id}")
    public ResponseEntity<CompletedSession> patch(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = sessionService.patchSession(id, body, userId);
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    // ── Alignment score ─────────────────────────────────────────────────────

    /**
     * Deterministic suggestion for how this session matched its scheduled workout, used to pre-fill
     * the rating modal. Readable by the session owner or their coach.
     */
    @GetMapping("/{id}/alignment/estimate")
    public ResponseEntity<AlignmentEstimator.AlignmentEstimate> estimateAlignment(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(alignmentService.estimate(id, userId));
    }

    /** Sets the athlete's own alignment rating (owner only). */
    @PutMapping("/{id}/alignment/athlete")
    public ResponseEntity<CompletedSession> setAthleteAlignment(@PathVariable String id,
            @RequestBody AlignmentScoreRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(alignmentService.setAthleteScore(id, request.score(), request.note(), userId));
    }

    /** Sets the coach's alignment rating, validating or overriding the athlete's (coach only). */
    @PutMapping("/{id}/alignment/coach")
    public ResponseEntity<CompletedSession> setCoachAlignment(@PathVariable String id,
            @RequestBody AlignmentScoreRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(alignmentService.setCoachScore(
                id, request.score(), request.note(), AlignmentScore.SOURCE_COACH, userId));
    }

    /**
     * Scored sessions over a date range for the alignment evolution chart, oldest first.
     * Pass athleteId (coach only) to read a coached athlete's history.
     */
    @GetMapping("/alignment/history")
    public ResponseEntity<List<SessionAlignmentService.AlignmentHistoryPoint>> alignmentHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String athleteId) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(alignmentService.history(userId, athleteId, from, to));
    }

    public record AlignmentScoreRequest(int score, String note) {}

    /** Deletes a session and its associated FIT file. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return sessionService.deleteSession(id, userId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // ── PMC endpoint ─────────────────────────────────────────────────────────

    /** Returns PMC data points (CTL, ATL, TSB) for the given date range. */
    @GetMapping("/pmc")
    public ResponseEntity<List<AnalyticsService.PmcDataPoint>> getPmc(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(analyticsService.generatePmc(userId, from, to));
    }

    // ── FIT file upload / download ────────────────────────────────────────────

    /** Uploads a FIT file and associates it with a session (replaces any existing file). */
    @PostMapping("/{id}/fit")
    public ResponseEntity<CompletedSession> uploadFit(@PathVariable String id,
            @RequestParam("file") MultipartFile file) throws IOException {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession result = fitFileService.uploadFitFile(id, userId, file.getInputStream());
        if (result != null) {
            // FIT file changed → previously cached/persisted curve is stale, force recompute on next access.
            powerCurveService.invalidateSessionPowerCurve(id);
        }
        return ResponseEntity.of(Optional.ofNullable(result));
    }

    /** Downloads the FIT file for a session (accessible to owner or their coach). */
    @GetMapping("/{id}/fit")
    public ResponseEntity<?> downloadFit(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return fitFileService.downloadFitFile(id, userId)
                .map(fit -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + fit.filename() + "\"")
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(14)).cachePrivate())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body((Object) fit.data()))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Analytics endpoints ─────────────────────────────────────────────

    /** Returns the best power curve across all sessions in the given date range. */
    @GetMapping("/power-curve")
    public ResponseEntity<Map<Integer, Double>> getPowerCurve(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(powerCurveService.getBestPowerCurve(userId, from, to));
    }

    /**
     * Returns the power curve for a single session, computed server-side from the stored
     * FIT file. Once a curve is computed it is immutable for the lifetime of that FIT file,
     * so we tell the browser to cache the response privately for 30 days.
     */
    @GetMapping("/{id}/power-curve")
    public ResponseEntity<Map<Integer, Double>> getSessionPowerCurve(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        Map<Integer, Double> curve = powerCurveService.getSessionPowerCurve(id, userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(curve);
    }

    /** Aggregates training volume by week or month for the given date range. */
    @GetMapping("/volume")
    public ResponseEntity<List<PowerCurveService.VolumeEntry>> getVolume(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "week") String groupBy) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(powerCurveService.computeVolume(userId, from, to, groupBy));
    }

    /** Returns all-time personal power records (best average power by duration). */
    @GetMapping("/personal-records")
    public ResponseEntity<Map<Integer, Double>> getPersonalRecords() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(powerCurveService.getPersonalRecords(userId));
    }
}
