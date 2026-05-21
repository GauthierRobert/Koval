package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.history.fit.FitFileStore;
import com.koval.trainingplannerbackend.training.metrics.NormalizedSpeedService;
import com.koval.trainingplannerbackend.training.model.SportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Owns the lifecycle of FIT files attached to completed sessions: storage
 * (delegated to {@link FitFileStore}), access checks, and the metric refresh
 * that must follow any FIT change.
 */
@Service
public class SessionFitFileService {

    private static final Logger log = LoggerFactory.getLogger(SessionFitFileService.class);

    private final CompletedSessionRepository repository;
    private final FitFileStore fitFileStore;
    private final AnalyticsService analyticsService;
    private final NormalizedSpeedService normalizedSpeedService;
    private final UserRepository userRepository;
    private final CoachService coachService;

    public SessionFitFileService(CompletedSessionRepository repository,
                                 FitFileStore fitFileStore,
                                 AnalyticsService analyticsService,
                                 NormalizedSpeedService normalizedSpeedService,
                                 UserRepository userRepository,
                                 CoachService coachService) {
        this.repository = repository;
        this.fitFileStore = fitFileStore;
        this.analyticsService = analyticsService;
        this.normalizedSpeedService = normalizedSpeedService;
        this.userRepository = userRepository;
        this.coachService = coachService;
    }

    /** FIT file binary data plus a suggested download filename. */
    public record FitFileResult(byte[] data, String filename) {}

    /**
     * Upload a FIT file and attach it to an existing session, replacing any previously attached file.
     * Returns {@code null} when the session is not found or not owned by the user.
     */
    public CompletedSession uploadFitFile(String sessionId, String userId, InputStream data) throws IOException {
        CompletedSession session = repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElse(null);
        if (session == null) return null;

        byte[] bytes = data.readAllBytes();
        fitFileStore.store(session, bytes);
        return recomputeMetricsAfterFitChange(session);
    }

    /**
     * After a FIT file has been attached to a session, recompute its normalized speed
     * (NGP for running, NSS for swimming) and resulting TSS/IF, then refresh user load.
     * Caller is responsible for setting the storage pointers on the session before invoking.
     */
    public CompletedSession recomputeMetricsAfterFitChange(CompletedSession session) {
        refreshNormalizedSpeedAndMetrics(session);
        CompletedSession saved = repository.save(session);
        if (saved.getUserId() != null) {
            analyticsService.recomputeAndSaveUserLoad(saved.getUserId());
        }
        return saved;
    }

    /**
     * Download the FIT file attached to a session, accessible by the session owner or their coach.
     * Returns empty when the session is missing, has no FIT file, or the requester lacks access.
     */
    public Optional<FitFileResult> downloadFitFile(String sessionId, String userId) {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfOwner(userId, s.getUserId()))
                .flatMap(s -> fitFileStore.read(s)
                        .map(bytes -> new FitFileResult(bytes, s.getId() + ".fit")));
    }

    /**
     * Delete the FIT file attached to a session from every backend that holds a copy.
     * Logs and swallows failures so cleanup paths don't fail loudly. Mutates the session's
     * pointer fields but does NOT save — the caller is expected to be in the middle of a
     * larger update (delete session, replace FIT, etc).
     */
    public void deleteFitFileQuietly(CompletedSession session) {
        if (session == null) return;
        fitFileStore.delete(session);
    }

    private void refreshNormalizedSpeedAndMetrics(CompletedSession session) {
        SportType sport = SportType.fromString(session.getSportType());
        if (sport != SportType.CYCLING) {
            normalizedSpeedService.computeFromFit(session, sport)
                    .ifPresent(session::setNormalizedSpeed);
        }
        userRepository.findById(session.getUserId())
                .ifPresent(user -> analyticsService.computeAndAttachMetrics(session, user));
    }

    private boolean isCoachOfOwner(String coachId, String athleteId) {
        try {
            return coachService.isCoachOfAthlete(coachId, athleteId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }
}
