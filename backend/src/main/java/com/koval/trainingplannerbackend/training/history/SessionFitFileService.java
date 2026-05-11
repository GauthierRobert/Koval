package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.UserRepository;
import com.koval.trainingplannerbackend.coach.CoachService;
import com.koval.trainingplannerbackend.training.metrics.NormalizedSpeedService;
import com.koval.trainingplannerbackend.training.model.SportType;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Owns the lifecycle of FIT files attached to completed sessions: GridFS upload/download/delete
 * and the metric refresh that must follow any FIT change.
 */
@Service
public class SessionFitFileService {

    private static final Logger log = LoggerFactory.getLogger(SessionFitFileService.class);

    private final CompletedSessionRepository repository;
    private final GridFsOperations gridFsOperations;
    private final AnalyticsService analyticsService;
    private final NormalizedSpeedService normalizedSpeedService;
    private final UserRepository userRepository;
    private final CoachService coachService;

    public SessionFitFileService(CompletedSessionRepository repository,
                                 GridFsOperations gridFsOperations,
                                 AnalyticsService analyticsService,
                                 NormalizedSpeedService normalizedSpeedService,
                                 UserRepository userRepository,
                                 CoachService coachService) {
        this.repository = repository;
        this.gridFsOperations = gridFsOperations;
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

    /**
     * Download the FIT file attached to a session, accessible by the session owner or their coach.
     * Returns empty when the session is missing, has no FIT file, or the requester lacks access.
     */
    public Optional<FitFileResult> downloadFitFile(String sessionId, String userId) {
        return repository.findById(sessionId)
                .filter(s -> userId.equals(s.getUserId()) || isCoachOfOwner(userId, s.getUserId()))
                .filter(s -> s.getFitFileId() != null)
                .map(s -> {
                    GridFSFile gridFile = gridFsOperations.findOne(
                            Query.query(Criteria.where("_id").is(new ObjectId(s.getFitFileId()))));
                    if (gridFile == null) return null;
                    GridFsResource resource = gridFsOperations.getResource(gridFile);
                    try {
                        return new FitFileResult(resource.getInputStream().readAllBytes(), s.getId() + ".fit");
                    } catch (IOException e) {
                        throw new UncheckedIOException("Failed to read FIT file for session " + s.getId(), e);
                    }
                });
    }

    /**
     * Delete a FIT file from GridFS by its ID, logging a warning on failure.
     * No-op if {@code fitFileId} is {@code null}.
     */
    public void deleteFitFileQuietly(String fitFileId) {
        if (fitFileId == null) return;
        try {
            gridFsOperations.delete(Query.query(Criteria.where("_id").is(new ObjectId(fitFileId))));
        } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
            log.warn("Failed to delete FIT file {}: {}", fitFileId, e.getMessage());
        }
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

    private boolean isCoachOfOwner(String coachId, String athleteId) {
        try {
            return coachService.isCoachOfAthlete(coachId, athleteId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }
}
