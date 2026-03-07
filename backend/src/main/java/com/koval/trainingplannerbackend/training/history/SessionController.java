package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.SecurityUtils;
import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutRepository;
import com.koval.trainingplannerbackend.training.TrainingRepository;
import com.koval.trainingplannerbackend.training.model.Training;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final CompletedSessionRepository repository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final CoachService coachService;
    private final GridFsOperations gridFsOperations;
    private final ScheduledWorkoutRepository scheduledWorkoutRepository;
    private final TrainingRepository trainingRepository;

    public SessionController(CompletedSessionRepository repository,
            AnalyticsService analyticsService,
            UserRepository userRepository,
            CoachService coachService,
            GridFsOperations gridFsOperations,
            ScheduledWorkoutRepository scheduledWorkoutRepository,
            TrainingRepository trainingRepository) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.gridFsOperations = gridFsOperations;
        this.scheduledWorkoutRepository = scheduledWorkoutRepository;
        this.trainingRepository = trainingRepository;
    }

    @PostMapping
    public ResponseEntity<CompletedSession> save(@RequestBody CompletedSession session) {
        String userId = SecurityUtils.getCurrentUserId();
        session.setUserId(userId);
        if (session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }

        // Compute TSS / IF before saving (sport-type-aware)
        userRepository.findById(userId).ifPresent(user -> analyticsService.computeAndAttachMetrics(session, user));

        // Auto-associate to a scheduled workout if none provided
        if (session.getScheduledWorkoutId() == null) {
            tryAutoAssociate(session, userId);
        }

        // Delete any synthetic session linked to this scheduled workout before saving real one
        if (session.getScheduledWorkoutId() != null) {
            deleteSyntheticSessionForSchedule(session.getScheduledWorkoutId());
        }

        CompletedSession saved = repository.save(session);

        // Update CTL/ATL/TSB on the user document
        analyticsService.recomputeAndSaveUserLoad(userId);

        // Link to scheduled workout if provided or auto-associated
        if (saved.getScheduledWorkoutId() != null) {
            try {
                coachService.markCompleted(saved.getScheduledWorkoutId(),
                        saved.getTss() != null ? saved.getTss().intValue() : null,
                        saved.getIntensityFactor(),
                        saved.getId());
            } catch (Exception ignored) {
                // Non-fatal if linking fails
            }
        }

        return ResponseEntity.ok(saved);
    }

    /**
     * If the given scheduled workout has a synthetic (planned) session linked, delete it
     * so the real session can take its place.
     */
    private void deleteSyntheticSessionForSchedule(String scheduledWorkoutId) {
        scheduledWorkoutRepository.findById(scheduledWorkoutId).ifPresent(sw -> {
            if (sw.getSessionId() != null) {
                repository.findById(sw.getSessionId()).ifPresent(existing -> {
                    if (existing.isSyntheticCompletion()) {
                        repository.delete(existing);
                        sw.setSessionId(null);
                        scheduledWorkoutRepository.save(sw);
                    }
                });
            }
        });
    }

    private void tryAutoAssociate(CompletedSession session, String userId) {
        LocalDate day = session.getCompletedAt().toLocalDate();
        List<ScheduledWorkout> candidates = scheduledWorkoutRepository
                .findByAthleteIdAndScheduledDate(userId, day);

        ScheduledWorkout best = null;
        int bestScore = 0;

        for (ScheduledWorkout sw : candidates) {
            if (!"PENDING".equals(sw.getStatus() != null ? sw.getStatus().name() : null)) continue;
            Optional<Training> trainingOpt = trainingRepository.findById(sw.getTrainingId());
            if (trainingOpt.isEmpty()) continue;
            int s = scoreCandidate(session, sw, trainingOpt.get());
            if (s > bestScore) {
                bestScore = s;
                best = sw;
            }
        }

        if (bestScore >= 60 && best != null) {
            session.setScheduledWorkoutId(best.getId());
        }
    }

    private static int scoreCandidate(CompletedSession session, ScheduledWorkout sw, Training training) {
        // Sport type match — hard gate
        String sessionSport = session.getSportType();
        if (training.getSportType() == null ||
                !training.getSportType().name().equalsIgnoreCase(sessionSport)) {
            return 0;
        }

        int score = 30; // sport match

        // Same calendar day
        if (sw.getScheduledDate() != null &&
                sw.getScheduledDate().equals(session.getCompletedAt().toLocalDate())) {
            score += 20;
        }

        // Duration proximity
        if (training.getEstimatedDurationSeconds() != null && training.getEstimatedDurationSeconds() > 0) {
            int planned = training.getEstimatedDurationSeconds();
            int actual = session.getTotalDurationSeconds();
            double ratio = Math.abs((double) (actual - planned)) / planned;
            if (ratio <= 0.20) {
                score += 25;
            } else if (ratio <= 0.40) {
                score += 10;
            }
        }

        // Title word overlap (5 pts per shared word, capped at 25)
        if (training.getTitle() != null && session.getTitle() != null) {
            Set<String> planWords = wordSet(training.getTitle());
            planWords.retainAll(wordSet(session.getTitle()));
            score += Math.min(planWords.size() * 5, 25);
        }

        return score;
    }

    private static Set<String> wordSet(String text) {
        Set<String> words = new HashSet<>();
        for (String w : text.toLowerCase().split("\\W+")) {
            if (w.length() > 2) words.add(w);
        }
        return words;
    }

    @GetMapping
    public ResponseEntity<List<CompletedSession>> list() {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(repository.findByUserIdOrderByCompletedAtDesc(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompletedSession> getById(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfAthlete(userId, s.getUserId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isCoachOfAthlete(String coachId, String athleteId) {
        try {
            return coachService.isCoachOfAthlete(coachId, athleteId);
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CompletedSession>> listForCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        String userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(repository.findByUserIdAndCompletedAtBetween(
                userId, start.atStartOfDay(), end.atTime(23, 59, 59)));
    }

    @PostMapping("/{sessionId}/link/{scheduledWorkoutId}")
    public ResponseEntity<CompletedSession> linkToSchedule(
            @PathVariable String sessionId,
            @PathVariable String scheduledWorkoutId) {
        String userId = SecurityUtils.getCurrentUserId();

        CompletedSession session = repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        // Clear old link if session was previously linked to a different scheduled workout
        String oldSwId = session.getScheduledWorkoutId();
        if (oldSwId != null && !oldSwId.equals(scheduledWorkoutId)) {
            scheduledWorkoutRepository.findById(oldSwId).ifPresent(oldSw -> {
                oldSw.setSessionId(null);
                scheduledWorkoutRepository.save(oldSw);
            });
        }

        // Delete any synthetic session already linked to the target scheduled workout
        deleteSyntheticSessionForSchedule(scheduledWorkoutId);

        session.setScheduledWorkoutId(scheduledWorkoutId);
        CompletedSession saved = repository.save(session);

        try {
            coachService.markCompleted(scheduledWorkoutId,
                    saved.getTss() != null ? saved.getTss().intValue() : null,
                    saved.getIntensityFactor(),
                    saved.getId());
        } catch (Exception ignored) {
            // Non-fatal
        }

        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CompletedSession> patch(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String userId = SecurityUtils.getCurrentUserId();
        CompletedSession session = repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
        if (session == null) return ResponseEntity.notFound().build();

        if (body.containsKey("rpe")) {
            int rpe = ((Number) body.get("rpe")).intValue();
            session.setRpe(rpe);
            if (session.getTss() == null) {
                double durationHours = session.getTotalDurationSeconds() / 3600.0;
                session.setTss(Math.pow(rpe / 10.0, 2) * durationHours * 100);
                session.setIntensityFactor(rpe / 10.0);
            }
        }

        CompletedSession saved = repository.save(session);
        analyticsService.recomputeAndSaveUserLoad(userId);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .map(s -> {
                    // Delete associated FIT file if present
                    if (s.getFitFileId() != null) {
                        try {
                            gridFsOperations.delete(
                                    Query.query(
                                            Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                        } catch (Exception ignored) {
                        }
                    }
                    repository.delete(s);
                    return ResponseEntity.noContent().build();
                })
                .orElse(ResponseEntity.notFound().build());
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
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .map(s -> {
                    try {
                        // Delete old FIT file if replacing
                        if (s.getFitFileId() != null) {
                            try {
                                gridFsOperations
                                        .delete(Query.query(Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                            } catch (Exception ignored) {
                            }
                        }
                        ObjectId fileId = gridFsOperations.store(
                                file.getInputStream(),
                                s.getId() + ".fit",
                                "application/octet-stream");
                        s.setFitFileId(fileId.toHexString());
                        return ResponseEntity.ok(repository.save(s));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/fit")
    public ResponseEntity<?> downloadFit(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .filter(s -> s.getFitFileId() != null)
                .map(s -> {
                    try {
                        GridFSFile gridFile = gridFsOperations
                                .findOne(Query.query(Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                        if (gridFile == null)
                            return ResponseEntity.notFound().build();

                        GridFsResource resource = gridFsOperations.getResource(gridFile);
                        byte[] bytes = resource.getInputStream().readAllBytes();
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + s.getId() + ".fit\"")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .body(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
