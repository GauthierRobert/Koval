package com.koval.trainingplannerbackend.training.history.fit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the FIT file storage layer. The GCS client itself is owned
 * by {@code storage.gcs.*} (see {@code MediaStorageProperties}); this block
 * only controls FIT-specific concerns (mode, bucket, object naming, migration).
 */
@ConfigurationProperties(prefix = "storage.fit")
public class FitStorageProperties {

    /**
     * Read/write strategy across the two backends. Default keeps writes flowing
     * to both Mongo and GCS while reads still come from Mongo — the safe
     * transition state.
     */
    private FitStorageMode mode = FitStorageMode.DUAL_WRITE_READ_MONGODB;

    /**
     * GCS bucket holding FIT objects. When blank, falls back to the bucket
     * configured for media uploads ({@code storage.gcs.bucket}).
     */
    private String bucket;

    /** Object key prefix; final layout is {@code {prefix}/{userId}/{sessionId}.fit}. */
    private String objectPrefix = "fit";

    private final Migration migration = new Migration();

    public FitStorageMode getMode() { return mode; }
    public void setMode(FitStorageMode mode) { this.mode = mode; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getObjectPrefix() { return objectPrefix; }
    public void setObjectPrefix(String objectPrefix) { this.objectPrefix = objectPrefix; }

    public Migration getMigration() { return migration; }

    /**
     * One-shot backfill that copies existing GridFS FIT files into GCS at
     * application startup. Disabled by default — flip on in the environment
     * that owns the migration, then turn back off once it's done.
     */
    public static class Migration {
        private boolean enabled = false;
        private int batchSize = 50;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
