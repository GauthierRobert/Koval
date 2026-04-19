package com.koval.trainingplannerbackend.integration.nolio.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persists a Nolio activity (delivered by Terra) into our history,
 * folding it onto an existing Strava session when the two overlap.
 *
 * Policy (agreed with user):
 *   - If an existing session for the same user has a non-null stravaActivityId
 *     and its completedAt falls within +/- 2 min of the Nolio start, AND
 *     sportType matches, annotate that session with nolioActivityId instead
 *     of creating a duplicate row.
 *   - Otherwise insert a fresh CompletedSession with nolioActivityId.
 *   - Replays (same nolioActivityId twice) are idempotent via the unique index
 *     and the by-id lookup below.
 */
@Service
public class NolioActivityIngestService {

    private static final Logger log = LoggerFactory.getLogger(NolioActivityIngestService.class);
    private static final long DEDUP_WINDOW_MINUTES = 2;

    private final NolioActivityMapper mapper;
    private final CompletedSessionRepository sessionRepository;

    public NolioActivityIngestService(NolioActivityMapper mapper, CompletedSessionRepository sessionRepository) {
        this.mapper = mapper;
        this.sessionRepository = sessionRepository;
    }

    public void ingest(User user, JsonNode activity) {
        CompletedSession candidate = mapper.map(activity);
        if (candidate.getNolioActivityId() == null) {
            log.warn("Nolio activity payload missing metadata.summary_id - skipping");
            return;
        }

        // Replay: already have this nolio activity stored.
        Optional<CompletedSession> existingNolio = sessionRepository
                .findByUserIdAndNolioActivityId(user.getId(), candidate.getNolioActivityId());
        if (existingNolio.isPresent()) {
            log.debug("Nolio activity {} already ingested for user {}", candidate.getNolioActivityId(), user.getId());
            return;
        }

        LocalDateTime completedAt = candidate.getCompletedAt();
        if (completedAt != null) {
            Optional<CompletedSession> overlap = findStravaOverlap(user.getId(), completedAt, candidate.getSportType());
            if (overlap.isPresent()) {
                CompletedSession merged = overlap.get();
                merged.setNolioActivityId(candidate.getNolioActivityId());
                enrichMissing(merged, candidate);
                sessionRepository.save(merged);
                log.info("Merged Nolio activity {} into existing Strava session {} for user {}",
                        candidate.getNolioActivityId(), merged.getId(), user.getId());
                return;
            }
        }

        candidate.setUserId(user.getId());
        CompletedSession saved = sessionRepository.save(candidate);
        log.info("Ingested Nolio activity {} as new session {} for user {}",
                candidate.getNolioActivityId(), saved.getId(), user.getId());
    }

    private Optional<CompletedSession> findStravaOverlap(String userId, LocalDateTime at, String sportType) {
        LocalDateTime from = at.minusMinutes(DEDUP_WINDOW_MINUTES);
        LocalDateTime to = at.plusMinutes(DEDUP_WINDOW_MINUTES);
        List<CompletedSession> candidates = sessionRepository.findByUserIdAndCompletedAtBetween(userId, from, to);
        return candidates.stream()
                .filter(s -> s.getStravaActivityId() != null)
                .filter(s -> sportType == null || sportType.equals(s.getSportType()))
                .findFirst();
    }

    /**
     * Fill in fields Strava didn't capture (e.g. swim stroke breakdown, cadence when Strava has 0).
     * Does not overwrite existing non-zero values.
     */
    private void enrichMissing(CompletedSession target, CompletedSession fromNolio) {
        if (target.getAvgPower() == 0 && fromNolio.getAvgPower() > 0) {
            target.setAvgPower(fromNolio.getAvgPower());
        }
        if (target.getAvgCadence() == 0 && fromNolio.getAvgCadence() > 0) {
            target.setAvgCadence(fromNolio.getAvgCadence());
        }
        if (target.getAvgHR() == 0 && fromNolio.getAvgHR() > 0) {
            target.setAvgHR(fromNolio.getAvgHR());
        }
        if (target.getTotalDistance() == null && fromNolio.getTotalDistance() != null) {
            target.setTotalDistance(fromNolio.getTotalDistance());
        }
    }
}
