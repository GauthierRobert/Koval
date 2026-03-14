package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Business logic for completed workout sessions.
 * Handles save orchestration, linking, patching, and deletion.
 */
@Service
public class SessionService {

    private final CompletedSessionRepository repository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final CoachService coachService;
    private final GridFsOperations gridFsOperations;
    private final SessionAssociationService associationService;

    public SessionService(CompletedSessionRepository repository,
                          AnalyticsService analyticsService,
                          UserRepository userRepository,
                          CoachService coachService,
                          GridFsOperations gridFsOperations,
                          SessionAssociationService associationService) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.gridFsOperations = gridFsOperations;
        this.associationService = associationService;
    }

    /**
     * Save a completed session: compute metrics, auto-associate, link to schedule, update user load.
     */
    public CompletedSession saveSession(CompletedSession session, String userId) {
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

        // Delete any synthetic session linked to this scheduled workout before saving real one
        if (session.getScheduledWorkoutId() != null) {
            associationService.deleteSyntheticSessionForSchedule(session.getScheduledWorkoutId());
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

        return saved;
    }

    /**
     * Manually link a session to a scheduled workout.
     */
    public CompletedSession linkSessionToSchedule(String sessionId, String scheduledWorkoutId, String userId) {
        CompletedSession session = repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
        if (session == null) return null;

        // Clear old link if session was previously linked to a different scheduled workout
        String oldSwId = session.getScheduledWorkoutId();
        if (oldSwId != null && !oldSwId.equals(scheduledWorkoutId)) {
            associationService.clearScheduledWorkoutLink(oldSwId);
        }

        // Delete any synthetic session already linked to the target scheduled workout
        associationService.deleteSyntheticSessionForSchedule(scheduledWorkoutId);

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

        return saved;
    }

    /**
     * Patch session fields (currently supports RPE).
     */
    public CompletedSession patchSession(String id, Map<String, Object> body, String userId) {
        CompletedSession session = repository.findById(id)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
        if (session == null) return null;

        if (body.containsKey("rpe")) {
            int rpe = ((Number) body.get("rpe")).intValue();
            session.setRpe(rpe);
            if (session.getTss() == null) {
                double intensityFactor = rpe / 10.0;
                session.setTss(TssCalculator.computeTss(session.getTotalDurationSeconds(), intensityFactor));
                session.setIntensityFactor(intensityFactor);
            }
        }

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
                    if (s.getFitFileId() != null) {
                        try {
                            gridFsOperations.delete(
                                    Query.query(Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                        } catch (Exception ignored) {
                        }
                    }
                    repository.delete(s);
                    return true;
                })
                .orElse(false);
    }
}
