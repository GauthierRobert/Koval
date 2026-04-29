package com.koval.trainingplannerbackend.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Daily cleanup of {@link Media} rows that were never confirmed by their uploader.
 * Removes both the GCS object (if present) and the Mongo document.
 */
@Component
public class MediaCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupScheduler.class);

    private final MediaRepository mediaRepository;
    private final MediaStorageService storage;

    public MediaCleanupScheduler(MediaRepository mediaRepository, MediaStorageService storage) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeUnconfirmedMedia() {
        if (!storage.isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(storage.getProperties().getUnconfirmedRetention());
        List<Media> stale = mediaRepository.findByConfirmedFalseAndCreatedAtBefore(cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("Purging {} unconfirmed media older than {}", stale.size(), cutoff);
        for (Media media : stale) {
            try {
                storage.deleteObject(media.getObjectName());
            } catch (Exception e) {
                log.warn("Failed to delete stale GCS object {}: {}", media.getObjectName(), e.getMessage());
            }
        }
        mediaRepository.deleteAll(stale);
    }
}
