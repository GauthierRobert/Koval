package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.club.feed.SessionCompletedEvent;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSession;
import com.koval.trainingplannerbackend.club.session.ClubTrainingSessionRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.training.metrics.TssCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lifecycle for completed workout sessions: save orchestration, linking, patching, and deletion.
 * Read-only window queries live in {@link SessionHistoryQueryService}; FIT file storage lives in
 * {@link SessionFitFileService}.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final CompletedSessionRepository repository;
    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final CoachService coachService;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final SessionAssociationService associationService;
    private final ClubTrainingSessionRepository clubTrainingSessionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SessionFitFileService fitFileService;

    public SessionService(CompletedSessionRepository repository,
                          AnalyticsService analyticsService,
                          UserRepository userRepository,
                          CoachService coachService,
                          ScheduledWorkoutService scheduledWorkoutService,
                          SessionAssociationService associationService,
                          ClubTrainingSessionRepository clubTrainingSessionRepository,
                          ApplicationEventPublisher eventPublisher,
                          SessionFitFileService fitFileService) {
        this.repository = repository;
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
        this.coachService = coachService;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.associationService = associationService;
        this.clubTrainingSessionRepository = clubTrainingSessionRepository;
        this.eventPublisher = eventPublisher;
        this.fitFileService = fitFileService;
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
                    fitFileService.deleteFitFileQuietly(s.getFitFileId());
                    repository.delete(s);
                    return true;
                })
                .orElse(false);
    }

    public List<CompletedSession> listSessions(String userId) {
        return repository.findByUserIdOrderByCompletedAtDesc(userId);
    }

    public Page<CompletedSession> listSessions(String userId, Pageable pageable) {
        return repository.findByUserIdOrderByCompletedAtDesc(userId, pageable);
    }

    /**
     * Retrieve a single session by ID, visible to the session owner or their coach.
     */
    public Optional<CompletedSession> getSession(String userId, String sessionId) {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfOwner(userId, s.getUserId()));
    }

    /**
     * List completed sessions for a user within a date range, suitable for calendar views.
     */
    public List<CompletedSession> listForCalendar(String userId, LocalDate start, LocalDate end) {
        return repository.findByUserIdAndCompletedAtBetween(
                userId, start.atStartOfDay(), end.atTime(23, 59, 59));
    }

    private void prepareSession(CompletedSession session, String userId) {
        session.setUserId(userId);
        if (session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }

        userRepository.findById(userId).ifPresent(user -> analyticsService.computeAndAttachMetrics(session, user));

        analyticsService.computeBlockDistances(session);

        if (session.getTotalDistance() == null && session.getBlockSummaries() != null) {
            double sum = session.getBlockSummaries().stream()
                    .map(CompletedSession.BlockSummary::distanceMeters)
                    .filter(java.util.Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (sum > 0) session.setTotalDistance(sum);
        }

        if (session.getScheduledWorkoutId() == null) {
            associationService.tryAutoAssociate(session, userId);
        }
    }

    private void deleteSyntheticIfLinked(CompletedSession session) {
        if (session.getScheduledWorkoutId() != null) {
            associationService.deleteSyntheticSessionForSchedule(session.getScheduledWorkoutId());
        }
    }

    private void postSaveSideEffects(CompletedSession saved, String userId) {
        analyticsService.recomputeAndSaveUserLoad(userId);

        if (saved.getScheduledWorkoutId() != null) {
            tryMarkCompleted(saved.getScheduledWorkoutId(), saved);
        }
    }

    private void clearPreviousLinkIfDifferent(CompletedSession session, String newScheduledWorkoutId) {
        String oldSwId = session.getScheduledWorkoutId();
        if (oldSwId != null && !oldSwId.equals(newScheduledWorkoutId)) {
            associationService.clearScheduledWorkoutLink(oldSwId);
        }
    }

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
