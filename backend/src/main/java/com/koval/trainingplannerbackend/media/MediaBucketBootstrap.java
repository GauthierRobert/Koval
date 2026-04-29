package com.koval.trainingplannerbackend.media;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the media bucket on application startup when running against
 * the local emulator (fake-gcs-server). In production we never auto-create
 * buckets — the bucket is provisioned via Terraform / gcloud and the app
 * has read/write IAM access only.
 */
@Component
@ConditionalOnBean(Storage.class)
public class MediaBucketBootstrap {

    private static final Logger log = LoggerFactory.getLogger(MediaBucketBootstrap.class);

    private final Storage storage;
    private final MediaStorageProperties properties;

    public MediaBucketBootstrap(Storage storage, MediaStorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucketExists() {
        if (properties.getEndpointUrl() == null || properties.getEndpointUrl().isBlank()) {
            // Production / real GCS: never auto-provision.
            return;
        }
        String name = properties.getBucket();
        if (name == null || name.isBlank()) {
            log.warn("storage.gcs.bucket is not set; skipping local bucket bootstrap");
            return;
        }
        try {
            Bucket existing = storage.get(name);
            if (existing != null) {
                log.info("GCS emulator bucket '{}' already exists", name);
                return;
            }
            storage.create(BucketInfo.newBuilder(name).build());
            log.info("Created GCS emulator bucket '{}'", name);
        } catch (StorageException e) {
            log.warn("Failed to ensure GCS emulator bucket '{}': {}", name, e.getMessage());
        }
    }
}
