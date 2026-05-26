package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.coach.ScheduledWorkout;
import com.koval.trainingplannerbackend.coach.ScheduledWorkoutService;
import com.koval.trainingplannerbackend.config.exceptions.ForbiddenOperationException;
import com.koval.trainingplannerbackend.config.exceptions.ResourceNotFoundException;
import com.koval.trainingplannerbackend.config.exceptions.ValidationException;
import com.koval.trainingplannerbackend.training.TrainingService;
import com.koval.trainingplannerbackend.training.model.Training;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Reads and writes the per-session {@link AlignmentScore}: a deterministic estimate, the athlete's
 * self-rating, and the coach/AI rating. Score-setting authorisation differs by role — the owner sets
 * the athlete score, a verified coach sets the coach score.
 */
@Service
public class SessionAlignmentService {

    private static final Logger log = LoggerFactory.getLogger(SessionAlignmentService.class);

    /** Plausible bounds for a manually entered alignment percentage. */
    static final int MIN_SCORE = 0;
    static final int MAX_SCORE = 300;
    private static final int MAX_NOTE_LENGTH = 5_000;

    private final CompletedSessionRepository repository;
    private final ScheduledWorkoutService scheduledWorkoutService;
    private final TrainingService trainingService;
    private final CoachService coachService;
    private final AlignmentEstimator estimator;

    public SessionAlignmentService(CompletedSessionRepository repository,
                                   ScheduledWorkoutService scheduledWorkoutService,
                                   TrainingService trainingService,
                                   CoachService coachService,
                                   AlignmentEstimator estimator) {
        this.repository = repository;
        this.scheduledWorkoutService = scheduledWorkoutService;
        this.trainingService = trainingService;
        this.coachService = coachService;
        this.estimator = estimator;
    }

    /**
     * Deterministic suggestion for how the session matched its scheduled workout, for pre-filling
     * the rating modal. Readable by the session owner or their coach.
     */
    public AlignmentEstimator.AlignmentEstimate estimate(String sessionId, String callerId) {
        CompletedSession session = findForRead(sessionId, callerId);
        return estimator.estimate(session, plannedTraining(session));
    }

    /** Set the athlete's own alignment rating. Only the session owner may call this. */
    public CompletedSession setAthleteScore(String sessionId, int score, String note, String callerId) {
        validate(score, note);
        CompletedSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        if (!callerId.equals(session.getUserId())) {
            throw new ForbiddenOperationException("Only the athlete can set their own alignment score");
        }
        AlignmentScore alignment = ensureAlignment(session);
        alignment.setAthleteScore(score);
        alignment.setAthleteNote(blankToNull(note));
        alignment.setAthleteSetAt(LocalDateTime.now());
        return repository.save(session);
    }

    /**
     * Set the coach rating (validating, overriding, or replacing the athlete's). Only a verified coach
     * of the session owner may call this.
     *
     * @param source {@link AlignmentScore#SOURCE_COACH} for a human coach, {@link AlignmentScore#SOURCE_AI}
     *               when set by an AI client
     */
    public CompletedSession setCoachScore(String sessionId, int score, String note, String source,
                                          String callerId) {
        validate(score, note);
        CompletedSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        if (!coachService.isCoachOfAthlete(callerId, session.getUserId())) {
            throw new ForbiddenOperationException("Not authorized: you are not the coach of this athlete");
        }
        AlignmentScore alignment = ensureAlignment(session);
        alignment.setCoachScore(score);
        alignment.setCoachNote(blankToNull(note));
        alignment.setCoachSource(AlignmentScore.SOURCE_AI.equals(source)
                ? AlignmentScore.SOURCE_AI : AlignmentScore.SOURCE_COACH);
        alignment.setCoachSetAt(LocalDateTime.now());
        return repository.save(session);
    }

    /**
     * Scored sessions over a date range for the evolution chart, oldest first. Only sessions that
     * carry an alignment rating are returned. Pass {@code athleteId} (coach only) to read an athlete's.
     */
    public List<AlignmentHistoryPoint> history(String callerId, String athleteId, LocalDate from, LocalDate to) {
        String subjectId = resolveSubject(callerId, athleteId);
        return repository.findByUserIdAndCompletedAtBetween(
                        subjectId, from.atStartOfDay(), to.atTime(23, 59, 59)).stream()
                .filter(s -> s.getAlignmentScore() != null && s.getAlignmentScore().effectiveScore() != null)
                .sorted(Comparator.comparing(CompletedSession::getCompletedAt))
                .map(AlignmentHistoryPoint::from)
                .toList();
    }

    /** Resolve whose data a read targets: self, or a coached athlete (requires a coaching relationship). */
    private String resolveSubject(String callerId, String athleteId) {
        if (athleteId == null || athleteId.isBlank() || athleteId.equals(callerId)) {
            return callerId;
        }
        if (!coachService.isCoachOfAthlete(callerId, athleteId)) {
            throw new ForbiddenOperationException("Not authorized: you are not the coach of this athlete");
        }
        return athleteId;
    }

    private CompletedSession findForRead(String sessionId, String callerId) {
        CompletedSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        boolean owner = callerId.equals(session.getUserId());
        if (!owner && !coachService.isCoachOfAthlete(callerId, session.getUserId())) {
            throw new ForbiddenOperationException("Not authorized to view this session");
        }
        return session;
    }

    /** Resolve the planned Training behind the session's scheduled workout, or null if unavailable. */
    private Training plannedTraining(CompletedSession session) {
        if (session.getScheduledWorkoutId() == null) return null;
        try {
            ScheduledWorkout sw = scheduledWorkoutService.getScheduledWorkout(session.getScheduledWorkoutId());
            return sw.getTrainingId() != null ? trainingService.getTrainingById(sw.getTrainingId()) : null;
        } catch (ResourceNotFoundException e) {
            log.debug("Planned training unavailable for session {}: {}", session.getId(), e.getMessage());
            return null;
        }
    }

    private AlignmentScore ensureAlignment(CompletedSession session) {
        if (session.getAlignmentScore() == null) {
            session.setAlignmentScore(new AlignmentScore());
        }
        return session.getAlignmentScore();
    }

    private void validate(int score, String note) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new ValidationException(
                    "Alignment score must be between " + MIN_SCORE + " and " + MAX_SCORE + " (percent)");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Note too long (max " + MAX_NOTE_LENGTH + " chars)");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** One point on the alignment evolution chart. */
    public record AlignmentHistoryPoint(String sessionId, String date, String title, String sportType,
                                        Integer athleteScore, Integer coachScore, Integer effectiveScore) {
        static AlignmentHistoryPoint from(CompletedSession s) {
            AlignmentScore a = s.getAlignmentScore();
            return new AlignmentHistoryPoint(
                    s.getId(),
                    s.getCompletedAt() != null ? s.getCompletedAt().toLocalDate().toString() : null,
                    s.getTitle(), s.getSportType(),
                    a.getAthleteScore(), a.getCoachScore(), a.effectiveScore());
        }
    }
}
