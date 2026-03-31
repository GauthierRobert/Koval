package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.feed.SessionCompletedEvent;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public SessionService(CompletedSessionRepository repository,
                          AnalyticsService analyticsService,
                          UserRepository userRepository,
                          CoachService coachService,
                          ScheduledWorkoutService scheduledWorkoutService,
                          GridFsOperations gridFsOperations,
                          SessionAssociationService associationService,
                          ClubTrainingSessionRepository clubTrainingSessionRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.gridFsOperations = gridFsOperations;
        this.associationService = associationService;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
        this.eventPublisher = eventPublisher;
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
        return repository.save(session);
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
                        throw new RuntimeException(e);
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
                session.setTss(TssCalculator.computeTss(session.getTotalDurationSeconds(), intensityFactor));
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
                    session.getTss() != null ? session.getTss().intValue() : null,
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
