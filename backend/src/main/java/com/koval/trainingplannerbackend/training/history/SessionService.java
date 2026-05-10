package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.feed.SessionCompletedEvent;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.training.metrics.NormalizedSpeedService;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for completed workout sessions.
 * Handles save orchestration, linking, patching, and deletion.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final CompletedSessionRepository repository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final CoachService coachService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final GridFsOperations gridFsOperations;
    private final SessionAssociationService associationService;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NormalizedSpeedService normalizedSpeedService;
    private final MongoTemplate mongoTemplate;

    public SessionService(CompletedSessionRepository repository,
                          AnalyticsService analyticsService,
                          UserRepository userRepository,
                          CoachService coachService,
                          ScheduledWorkoutService scheduledWorkoutService,
                          GridFsOperations gridFsOperations,
                          SessionAssociationService associationService,
                          ClubTrainingSessionRepository clubTrainingSessionRepository,
                          ApplicationEventPublisher eventPublisher,
                          NormalizedSpeedService normalizedSpeedService,
                          MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.gridFsOperations = gridFsOperations;
        this.associationService = associationService;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.normalizedSpeedService = normalizedSpeedService;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Save a completed session: compute metrics, auto-associate, link to schedule, update user load.
     */
    public CompletedSession saveSession(CompletedSession session, String userId) {
        prepareSession(session, userId);
        deleteSyntheticIfLinked(session);

        CompletedSession saved = repository.save(session);

        postSaveSideEffects(saved, userId);
        eventPublisher.publishEvent(new SessionCompletedEvent(saved));
        return saved;
    }

    /**
     * Manually link a session to a scheduled workout.
     */
    public CompletedSession linkSessionToSchedule(String sessionId, String scheduledWorkoutId, String userId) {
        CompletedSession session = findOwnedSession(sessionId, userId);
        if (session == null) return null;

        clearPreviousLinkIfDifferent(session, scheduledWorkoutId);

        // Delete any synthetic session already linked to the target scheduled workout
        associationService.deleteSyntheticSessionForSchedule(scheduledWorkoutId);

        session.setScheduledWorkoutId(scheduledWorkoutId);
        CompletedSession saved = repository.save(session);
        tryMarkCompleted(scheduledWorkoutId, saved);
        return saved;
    }

    /**
     * Manually link a session to a club training session.
     */
    public CompletedSession linkSessionToClubSession(String sessionId, String clubSessionId, String userId) {
        CompletedSession session = findOwnedSession(sessionId, userId);
        if (session == null) return null;

        ClubTrainingSession clubSession = clubTrainingSessionRepository.findById(clubSessionId).orElse(null);
        if (clubSession == null || !clubSession.getParticipantIds().contains(userId)) return null;

        session.setClubSessionId(clubSessionId);
        CompletedSession saved = repository.save(session);
        eventPublisher.publishEvent(new SessionCompletedEvent(saved));
        return saved;
    }

    /**
     * Patch session fields (currently supports RPE).
     */
    public CompletedSession patchSession(String id, Map<String, Object> body, String userId) {
        CompletedSession session = findOwnedSession(id, userId);
        if (session == null) return null;

        applyRpePatch(session, body);

        CompletedSession saved = repository.save(session);
        analyticsService.recomputeAndSaveUserLoad(userId);
        return saved;
    }

    /**
     * Delete a session and its associated FIT file.
     */
    public boolean deleteSession(String id, String userId) {
        return repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .map(s -> {
                    deleteFitFileQuietly(s.getFitFileId());
                    repository.delete(s);
                    return true;
                })
                .orElse(false);
    }

    // ── Query methods ────────────────────────────────────────────────────────

    /**
     * List all completed sessions for the given user, ordered by completion date descending.
     *
     * @param userId the ID of the user whose sessions to list
     * @return all sessions for the user, most recent first
     */
    public List<CompletedSession> listSessions(String userId) {
        return repository.findByUserIdOrderByCompletedAtDesc(userId);
    }

    /**
     * List completed sessions for the given user with pagination, ordered by completion date descending.
     *
     * @param userId   the ID of the user whose sessions to list
     * @param pageable pagination parameters
     * @return a page of sessions for the user, most recent first
     */
    public Page<CompletedSession> listSessions(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByCompletedAtDesc(userId, pageable);
    }

    /**
     * Retrieve a single session by ID, visible to the session owner or their coach.
     *
     * @param userId    the ID of the requesting user (owner or coach)
     * @param sessionId the ID of the session to retrieve
     * @return the session if found and the user has access, empty otherwise
     */
    public Optional<CompletedSession> getSession(String userId, String sessionId) {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfOwner(userId, s.getUserId()));
    }

    /**
     * List completed sessions for a user within a date range, suitable for calendar views.
     *
     * @param userId the ID of the user whose sessions to list
     * @param start  the start date (inclusive)
     * @param end    the end date (inclusive)
     * @return sessions completed within the given date range
     */
    public List<CompletedSession> listForCalendar(String userId, LocalDate start, LocalDate end) {
        return repository.findByUserIdAndCompletedAtBetween(
                userId, start.atStartOfDay(), end.atTime(23, 59, 59));
    }

    // ── Windowed history listing (for paginated, week-aligned, filterable browsing) ──

    /**
     * Filters that may be applied to a window query.
     * All fields are optional; null values mean "no constraint on this dimension".
     */
    public record WindowFilters(
            String sport,
            LocalDate from,
            LocalDate to,
            Integer durationMinSec,
            Integer durationMaxSec,
            Double tssMin,
            Double tssMax) {}

    /**
     * Result of a window query. {@code windowStart} and {@code windowEnd} bracket a
     * Monday-aligned date range (windowEnd exclusive), so the client can group by
     * week without ever seeing a partial week split across pages.
     */
    public record SessionWindowResult(
            List<CompletedSession> sessions,
            LocalDate windowStart,
            LocalDate windowEnd,
            boolean hasMore) {}

    /**
     * List completed sessions in a Monday-aligned window of size {@code weeks} ending
     * just before {@code before}. {@code before} is snapped up to the next Monday if
     * it isn't already one, so weeks are never cut at a page boundary.
     *
     * Pagination contract: to fetch the previous window, pass {@code before =
     * result.windowStart()}. Stop when {@code result.hasMore()} is false.
     */
    public SessionWindowResult listWindow(String userId, LocalDate before, int weeks, WindowFilters f) {
        int clampedWeeks = Math.max(1, Math.min(52, weeks));
        LocalDate windowEnd = snapToNextMondayInclusive(before != null
                ? before
                : LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        LocalDate windowStart = windowEnd.minusWeeks(clampedWeeks);

        // Intersect window range with optional from/to filters.
        LocalDateTime rangeLo = windowStart.atStartOfDay();
        LocalDateTime rangeHi = windowEnd.atStartOfDay();
        if (f != null && f.from() != null) {
            LocalDateTime fromDt = f.from().atStartOfDay();
            if (fromDt.isAfter(rangeLo)) rangeLo = fromDt;
        }
        if (f != null && f.to() != null) {
            LocalDateTime toDt = f.to().plusDays(1).atStartOfDay();
            if (toDt.isBefore(rangeHi)) rangeHi = toDt;
        }
        if (!rangeLo.isBefore(rangeHi)) {
            return new SessionWindowResult(Collections.emptyList(), windowStart, windowEnd,
                    hasMoreBefore(userId, windowStart, f));
        }

        Criteria criteria = Criteria.where("userId").is(userId)
                .and("completedAt").gte(rangeLo).lt(rangeHi);
        applyNonDateFilters(criteria, f);

        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "completedAt"));
        List<CompletedSession> sessions = mongoTemplate.find(query, CompletedSession.class);

        return new SessionWindowResult(sessions, windowStart, windowEnd,
                hasMoreBefore(userId, windowStart, f));
    }

    /**
     * Returns true if any session matching {@code f} exists with completedAt strictly
     * before {@code windowStart}. Used to drive the "load older" affordance.
     */
    private boolean hasMoreBefore(String userId, LocalDate windowStart, WindowFilters f) {
        LocalDateTime hi = windowStart.atStartOfDay();
        // If a from-filter is at or after the window's lower edge, there's nothing older to find.
        if (f != null && f.from() != null && !f.from().atStartOfDay().isBefore(hi)) {
            return false;
        }
        Criteria c = Criteria.where("userId").is(userId).and("completedAt").lt(hi);
        if (f != null && f.from() != null) {
            // Re-apply the lower bound to avoid scanning the whole history when from is set.
            c = Criteria.where("userId").is(userId)
                    .and("completedAt").gte(f.from().atStartOfDay()).lt(hi);
        }
        applyNonDateFilters(c, f);
        return mongoTemplate.exists(new Query(c), CompletedSession.class);
    }

    private void applyNonDateFilters(Criteria c, WindowFilters f) {
        if (f == null) return;
        if (f.sport() != null && !f.sport().isBlank()) {
            c.and("sportType").is(f.sport());
        }
        if (f.durationMinSec() != null && f.durationMaxSec() != null) {
            c.and("totalDurationSeconds").gte(f.durationMinSec()).lte(f.durationMaxSec());
        } else if (f.durationMinSec() != null) {
            c.and("totalDurationSeconds").gte(f.durationMinSec());
        } else if (f.durationMaxSec() != null) {
            c.and("totalDurationSeconds").lte(f.durationMaxSec());
        }
        if (f.tssMin() != null && f.tssMax() != null) {
            c.and("tss").gte(f.tssMin()).lte(f.tssMax());
        } else if (f.tssMin() != null) {
            c.and("tss").gte(f.tssMin());
        } else if (f.tssMax() != null) {
            c.and("tss").lte(f.tssMax());
        }
    }

    private LocalDate snapToNextMondayInclusive(LocalDate d) {
        int dow = d.getDayOfWeek().getValue(); // 1=Mon..7=Sun
        return dow == 1 ? d : d.plusDays((8 - dow) % 7);
    }

    // ── FIT file operations ─────────────────────────────────────────────────

    /**
     * Result record containing FIT file binary data and its filename.
     *
     * @param data     the raw FIT file bytes
     * @param filename the suggested filename for download
     */
    public record FitFileResult(byte[] data, String filename) {}

    /**
     * Upload a FIT file and attach it to an existing session, replacing any previously attached file.
     *
     * @param sessionId the ID of the session to attach the file to
     * @param userId    the ID of the session owner
     * @param data      the FIT file input stream
     * @return the updated session, or {@code null} if the session was not found or not owned by the user
     * @throws IOException if an I/O error occurs while storing the file
     */
    public CompletedSession uploadFitFile(String sessionId, String userId, InputStream data) throws IOException {
        CompletedSession session = findOwnedSession(sessionId, userId);
        if (session == null) return null;

        deleteFitFileQuietly(session.getFitFileId());
        ObjectId fileId = gridFsOperations.store(data, session.getId() + ".fit", "application/octet-stream");
        session.setFitFileId(fileId.toHexString());
        return recomputeMetricsAfterFitChange(session);
    }

    /**
     * After a FIT file has been attached to a session, recompute its normalized speed
     * (NGP for running, NSS for swimming) and resulting TSS/IF, then refresh user load.
     * Caller is responsible for setting {@code session.fitFileId} before invoking.
     */
    public CompletedSession recomputeMetricsAfterFitChange(CompletedSession session) {
        refreshNormalizedSpeedAndMetrics(session);
        CompletedSession saved = repository.save(session);
        if (saved.getUserId() != null) {
            analyticsService.recomputeAndSaveUserLoad(saved.getUserId());
        }
        return saved;
    }

    private void refreshNormalizedSpeedAndMetrics(CompletedSession session) {
        SportType sport = SportType.fromString(session.getSportType());
        if (sport != SportType.CYCLING) {
            normalizedSpeedService.computeFromFit(session.getFitFileId(), sport)
                    .ifPresent(session::setNormalizedSpeed);
        }
        userRepository.findById(session.getUserId())
                .ifPresent(user -> analyticsService.computeAndAttachMetrics(session, user));
    }

    /**
     * Download the FIT file attached to a session, accessible by the session owner or their coach.
     *
     * @param sessionId the ID of the session whose FIT file to download
     * @param userId    the ID of the requesting user (owner or coach)
     * @return the FIT file data and filename, or empty if not found or not accessible
     * @throws IOException if an I/O error occurs while reading the file
     */
    public Optional<FitFileResult> downloadFitFile(String sessionId, String userId) throws IOException {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfOwner(userId, s.getUserId()))
                .filter(s -> s.getFitFileId() != null)
                .map(s -> {
                    try {
                        GridFSFile gridFile = gridFsOperations.findOne(
                                Query.query(Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                        if (gridFile == null) return null;
                        GridFsResource resource = gridFsOperations.getResource(gridFile);
                        return new FitFileResult(resource.getInputStream().readAllBytes(), s.getId() + ".fit");
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read FIT file for session " + s.getId(), e);
                    }
                });
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Set userId, completedAt, compute metrics, block distances, and auto-associate to a schedule.
     */
    private void prepareSession(CompletedSession session, String userId) {
        session.setUserId(userId);
        if (session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }

        // Compute TSS / IF before saving (sport-type-aware)
        userRepository.findById(userId).ifPresent(user -> analyticsService.computeAndAttachMetrics(session, user));

        // Estimate per-block distance when not provided
        analyticsService.computeBlockDistances(session);

        // Roll block distances up to session total when not already set
        if (session.getTotalDistance() == null && session.getBlockSummaries() != null) {
            double sum = session.getBlockSummaries().stream()
                    .map(CompletedSession.BlockSummary::distanceMeters)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (sum > 0) session.setTotalDistance(sum);
        }

        // Auto-associate to a scheduled workout if none provided
        if (session.getScheduledWorkoutId() == null) {
            associationService.tryAutoAssociate(session, userId);
        }
    }

    /**
     * Delete any synthetic session linked to this scheduled workout before saving the real one.
     */
    private void deleteSyntheticIfLinked(CompletedSession session) {
        if (session.getScheduledWorkoutId() != null) {
            associationService.deleteSyntheticSessionForSchedule(session.getScheduledWorkoutId());
        }
    }

    /**
     * Recompute user load and mark the scheduled workout as completed after saving.
     */
    private void postSaveSideEffects(CompletedSession saved, String userId) {
        // Update CTL/ATL/TSB on the user document
        analyticsService.recomputeAndSaveUserLoad(userId);

        // Link to scheduled workout if provided or auto-associated
        if (saved.getScheduledWorkoutId() != null) {
            tryMarkCompleted(saved.getScheduledWorkoutId(), saved);
        }
    }

    /**
     * Clear the old scheduled workout link if the session was previously linked to a different one.
     */
    private void clearPreviousLinkIfDifferent(CompletedSession session, String newScheduledWorkoutId) {
        String oldSwId = session.getScheduledWorkoutId();
        if (oldSwId != null && !oldSwId.equals(newScheduledWorkoutId)) {
            associationService.clearScheduledWorkoutLink(oldSwId);
        }
    }

    /**
     * Apply RPE-related fields from the patch body to the session, computing TSS if absent.
     */
    private void applyRpePatch(CompletedSession session, Map<String, Object> body) {
        if (body.containsKey("rpe")) {
            int rpe = ((Number) body.get("rpe")).intValue();
            session.setRpe(rpe);
            if (session.getTss() == null) {
                double intensityFactor = rpe / 10.0;
                session.setTss(TssCalculator.computeTss(AnalyticsService.loadDurationSeconds(session), intensityFactor));
                session.setIntensityFactor(intensityFactor);
            }
        }
    }

    /**
     * Delete a FIT file from GridFS by its ID, logging a warning on failure.
     * No-op if {@code fitFileId} is {@code null}.
     */
    private void deleteFitFileQuietly(String fitFileId) {
        if (fitFileId != null) {
            try {
                gridFsOperations.delete(
                        Query.query(Criteria.where("_id").is(new ObjectId(fitFileId))));
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                log.warn("Failed to delete FIT file {}: {}", fitFileId, e.getMessage());
            }
        }
    }

    private CompletedSession findOwnedSession(String sessionId, String userId) {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
    }

    private void tryMarkCompleted(String scheduledWorkoutId, CompletedSession session) {
        try {
            scheduledWorkoutService.markCompleted(scheduledWorkoutId,
                    Optional.ofNullable(session.getTss()).map(Double::intValue).orElse(null),
                    session.getIntensityFactor(),
                    session.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Failed to mark scheduled workout {} as completed: {}", scheduledWorkoutId, e.getMessage());
        }
    }

    private boolean isCoachOfOwner(String coachId, String athleteId) {
        try {
            return coachService.isCoachOfAthlete(coachId, athleteId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }
}
