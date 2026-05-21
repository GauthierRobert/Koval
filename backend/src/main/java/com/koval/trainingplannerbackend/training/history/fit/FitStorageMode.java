package com.koval.trainingplannerbackend.training.history.fit;

/**
 * Where FIT files are written and read. Lets us roll out GCS-backed FIT storage
 * one step at a time without losing data.
 *
 * <p>Recommended progression:
 * <ol>
 *   <li>{@link #MONGODB_ONLY} — current state, before GCS is configured.</li>
 *   <li>{@link #DUAL_WRITE_READ_MONGODB} — turn on after running the backfill;
 *       every new FIT lands in both places, reads still come from Mongo so any
 *       GCS misconfiguration is invisible to users.</li>
 *   <li>{@link #DUAL_WRITE_READ_GCS} — flip the read source once we trust GCS;
 *       Mongo keeps receiving writes as a safety net.</li>
 *   <li>{@link #GCS_ONLY} — stop writing to Mongo and start saving the storage
 *       bill. New uploads no longer hit GridFS.</li>
 * </ol>
 */
public enum FitStorageMode {
    MONGODB_ONLY,
    DUAL_WRITE_READ_MONGODB,
    DUAL_WRITE_READ_GCS,
    GCS_ONLY;

    public boolean writesMongo() {
        return this != GCS_ONLY;
    }

    public boolean writesGcs() {
        return this != MONGODB_ONLY;
    }

    public boolean readsGcsFirst() {
        return this == DUAL_WRITE_READ_GCS || this == GCS_ONLY;
    }
}
