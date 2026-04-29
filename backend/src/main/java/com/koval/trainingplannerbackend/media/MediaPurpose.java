package com.koval.trainingplannerbackend.media;

/**
 * What the media is being uploaded for. Drives the GCS object name prefix
 * and the authorization rules applied at upload/read time.
 */
public enum MediaPurpose {
    GAZETTE_POST,
    FEED_POST_ENRICHMENT,
    ANNOUNCEMENT_ATTACHMENT,
    AVATAR
}
