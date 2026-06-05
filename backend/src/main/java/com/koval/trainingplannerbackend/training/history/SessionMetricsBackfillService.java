package com.koval.trainingplannerbackend.training.history;

import com.koval.trainingplannerbackend.auth.ThresholdReferenceChangedEvent;
import com.koval.trainingplannerbackend.auth.User;
import com.koval.trainingplannerbackend.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Recomputes TSS/IF on sessions whose metrics could not be derived when they were ingested —
 * typically history imported before the athlete set FTP/FTPace/CSS (tss = null), or sessions
 * estimated from RPE because no threshold reference was available (tssFromRpe = true).
 *
 * <p>Triggered by {@link ThresholdReferenceChangedEvent} so that setting a threshold reference
 * retroactively fills the volume graphs and PMC, which aggregate the persisted {@code tss} field.
 * Sessions that already carry a power/pace-derived TSS are never touched: TSS is intentionally
 * computed against the reference in effect at ingest time.
 */
@Service
public class SessionMetricsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(SessionMetricsBackfillService.class);

    private final CompletedSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public SessionMetricsBackfillService(CompletedSessionRepository sessionRepository,
                                         UserRepository userRepository,
                                         AnalyticsService analyticsService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    @EventListener
    @Async
    public void onThresholdReferenceChanged(ThresholdReferenceChangedEvent event) {
        try {
            backfillMissingMetrics(event.userId());
        } catch (Exception e) {
            log.error("TSS backfill failed for user {}: {}", event.userId(), e.getMessage(), e);
        }
    }

    /**
     * Recompute metrics on every backfill candidate session and persist the ones that changed.
     * Refreshes the user's CTL/ATL/TSB afterwards, since those are EMAs over persisted TSS.
     */
    public void backfillMissingMetrics(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        List<CompletedSession> candidates = sessionRepository.findMetricsBackfillCandidatesByUserId(userId);
        long updated = candidates.stream()
                .filter(session -> recomputeMetrics(session, user))
                .peek(sessionRepository::save)
                .count();

        if (updated > 0) {
            analyticsService.recomputeAndSaveUserLoad(userId);
            log.info("Backfilled TSS/IF on {} session(s) for user {}", updated, userId);
        }
    }

    /**
     * Clears the session's metrics and re-runs the standard computation against the user's
     * current threshold references. Restores the previous values when the recompute yields
     * nothing (never erases data) and reports whether anything actually changed.
     */
    private boolean recomputeMetrics(CompletedSession session, User user) {
        Double previousTss = session.getTss();
        Double previousIf = session.getIntensityFactor();
        Boolean previousFromRpe = session.getTssFromRpe();

        session.setTss(null);
        session.setIntensityFactor(null);
        analyticsService.computeAndAttachMetrics(session, user);

        if (session.getTss() == null) {
            session.setTss(previousTss);
            session.setIntensityFactor(previousIf);
            session.setTssFromRpe(previousFromRpe);
            return false;
        }

        return !Objects.equals(previousTss, session.getTss())
                || !Objects.equals(previousIf, session.getIntensityFactor());
    }
}
