package com.koval.trainingplannerbackend.training.history.fit;

import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.koval.trainingplannerbackend.training.history.CompletedSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * One-shot backfill: copy every existing GridFS-stored FIT file into GCS so we
 * can later flip the read source over without losing access to historical data.
 *
 * <p>Disabled by default — opt in per environment with
 * {@code storage.fit.migration.enabled=true}. Always runs off the main thread so
 * the application stays responsive while the copy is in progress; subsequent
 * restarts pick up wherever the previous run stopped because the work is
 * idempotent (sessions that already have {@code fitGcsObject} are skipped).
 */
@Component
public class FitFileMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FitFileMigrationRunner.class);

    private final CompletedSessionRepository sessionRepository;
    private final FitFileStore fitFileStore;
    private final FitStorageProperties properties;

    public FitFileMigrationRunner(CompletedSessionRepository sessionRepository,
                                  FitFileStore fitFileStore,
                                  FitStorageProperties properties) {
        this.sessionRepository = sessionRepository;
        this.fitFileStore = fitFileStore;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getMigration().isEnabled()) {
            log.debug("FIT → GCS migration is disabled (storage.fit.migration.enabled=false).");
            return;
        }
        if (!fitFileStore.isGcsAvailable()) {
            log.warn("FIT → GCS migration requested but GCS client is not configured; skipping.");
            return;
        }
        Thread.ofVirtual().name("fit-gcs-migration").start(this::runMigration);
    }

    private void runMigration() {
        int batchSize = Math.max(1, properties.getMigration().getBatchSize());
        int copied = 0;
        int failed = 0;
        int skipped = 0;
        long start = System.currentTimeMillis();

        log.info("Starting FIT → GCS backfill (batch size = {}).", batchSize);
        // The query always asks for page 0; sessions drop out of the candidate set
        // as their fitGcsObject is set, so this naturally drains the queue. We bail
        // when a page produces no copies (everything left has broken pointers or
        // keeps failing — manual intervention required, no point spinning).
        while (true) {
            Page<CompletedSession> page = sessionRepository.findFitMigrationCandidates(
                    PageRequest.of(0, batchSize));
            if (page.isEmpty()) break;

            int pageCopied = 0;
            for (CompletedSession session : page) {
                try {
                    if (fitFileStore.backfillToGcs(session)) {
                        sessionRepository.save(session);
                        pageCopied++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("FIT backfill failed for session {} (fitFileId={}): {}",
                            session.getId(), session.getFitFileId(), e.getMessage());
                }
            }
            copied += pageCopied;
            if (pageCopied == 0) {
                log.warn("FIT → GCS backfill stalled: {} remaining sessions could not be copied. "
                        + "Resolve manually (likely orphan GridFS pointers) and rerun.",
                        page.getNumberOfElements());
                break;
            }
        }

        log.info("FIT → GCS backfill done in {} ms: copied={}, skipped={}, failed={}.",
                System.currentTimeMillis() - start, copied, skipped, failed);
    }
}
