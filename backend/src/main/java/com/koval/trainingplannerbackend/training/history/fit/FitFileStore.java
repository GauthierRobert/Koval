package com.koval.trainingplannerbackend.training.history.fit;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.koval.trainingplannerbackend.media.MediaStorageProperties;
import com.koval.trainingplannerbackend.training.history.CompletedSession;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Storage façade for FIT files. Owns the dual MongoDB-GridFS / GCS layout and
 * the {@link FitStorageMode}-driven read/write routing. Pure storage — no
 * access checks, no metric recomputation; that's {@code SessionFitFileService}.
 *
 * <p>Pointer fields on {@link CompletedSession} ({@code fitFileId} for GridFS,
 * {@code fitGcsObject} for GCS) are mutated in place; the caller is
 * responsible for persisting the session.
 */
@Service
public class FitFileStore {

    private static final Logger log = LoggerFactory.getLogger(FitFileStore.class);
    private static final String CONTENT_TYPE = "application/octet-stream";

    private final GridFsOperations gridFsOperations;
    private final ObjectProvider<Storage> storageProvider;
    private final FitStorageProperties fitProperties;
    private final MediaStorageProperties mediaProperties;
    private final Executor asyncExecutor;
    private final MongoTemplate mongoTemplate;

    public FitFileStore(GridFsOperations gridFsOperations,
                        ObjectProvider<Storage> storageProvider,
                        FitStorageProperties fitProperties,
                        MediaStorageProperties mediaProperties,
                        @Qualifier("taskExecutor") Executor asyncExecutor,
                        MongoTemplate mongoTemplate) {
        this.gridFsOperations = gridFsOperations;
        this.storageProvider = storageProvider;
        this.fitProperties = fitProperties;
        this.mediaProperties = mediaProperties;
        this.asyncExecutor = asyncExecutor;
        this.mongoTemplate = mongoTemplate;
    }

    public FitStorageMode mode() {
        return fitProperties.getMode();
    }

    /**
     * Persist FIT bytes to every backend the current mode targets. Mutates the
     * session's pointer fields ({@code fitFileId}, {@code fitGcsObject}); the
     * previous GridFS/GCS objects are deleted first so the session never points
     * at orphan data.
     */
    public void store(CompletedSession session, byte[] bytes) {
        delete(session);

        FitStorageMode mode = fitProperties.getMode();
        if (mode.writesMongo()) {
            ObjectId fileId = gridFsOperations.store(
                    new ByteArrayInputStream(bytes),
                    session.getId() + ".fit",
                    CONTENT_TYPE);
            session.setFitFileId(fileId.toHexString());
        }
        if (mode.writesGcs() && isGcsAvailable()) {
            String objectName = objectName(session);
            if (mode == FitStorageMode.GCS_ONLY) {
                // Only copy — write synchronously so the caller knows it landed.
                writeToGcs(objectName, bytes);
                session.setFitGcsObject(objectName);
            } else {
                // Dual mode: GCS is a copy of Mongo. Defer it so request latency
                // and tail-jitter from GCS don't block the athlete's upload.
                // The pointer is persisted by the async task on success.
                String sessionId = session.getId();
                asyncExecutor.execute(() -> writeToGcsAsync(sessionId, objectName, bytes));
            }
        }
    }

    private void writeToGcsAsync(String sessionId, String objectName, byte[] bytes) {
        try {
            writeToGcs(objectName, bytes);
        } catch (RuntimeException e) {
            log.warn("Async GCS write failed for session {} (object={}); Mongo copy remains: {}",
                    sessionId, objectName, e.getMessage());
            return;
        }
        try {
            // Partial update on the pointer field only — avoids races with the
            // caller's full-document save of the same session.
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(sessionId)),
                    Update.update("fitGcsObject", objectName),
                    CompletedSession.class);
        } catch (RuntimeException e) {
            log.warn("Async GCS pointer update failed for session {} (object={}): {}",
                    sessionId, objectName, e.getMessage());
        }
    }

    /**
     * Read FIT bytes for a session, honoring the configured read source with a
     * fallback to the other backend when the primary has no copy yet (handy
     * during the dual-write window).
     */
    public Optional<byte[]> read(CompletedSession session) {
        if (session == null) return Optional.empty();
        boolean gcsFirst = fitProperties.getMode().readsGcsFirst();

        Optional<byte[]> primary = gcsFirst ? readFromGcs(session) : readFromMongo(session);
        if (primary.isPresent()) return primary;
        return gcsFirst ? readFromMongo(session) : readFromGcs(session);
    }

    /**
     * Remove the FIT bytes from every backend currently holding a copy and
     * clear the session's pointer fields. Errors are logged and swallowed —
     * cleanup must not fail loud.
     */
    public void delete(CompletedSession session) {
        if (session == null) return;
        if (session.getFitFileId() != null) {
            try {
                gridFsOperations.delete(Query.query(Criteria.where("_id").is(new ObjectId(session.getFitFileId()))));
            } catch (IllegalArgumentException | org.springframework.dao.DataAccessException e) {
                log.warn("Failed to delete GridFS FIT {}: {}", session.getFitFileId(), e.getMessage());
            }
            session.setFitFileId(null);
        }
        if (session.getFitGcsObject() != null && isGcsAvailable()) {
            try {
                storageProvider.getObject().delete(BlobId.of(bucket(), session.getFitGcsObject()));
            } catch (RuntimeException e) {
                log.warn("Failed to delete GCS FIT object {}: {}", session.getFitGcsObject(), e.getMessage());
            }
            session.setFitGcsObject(null);
        }
    }

    /**
     * Copy the GridFS-stored FIT for a session up to GCS, leaving the GridFS
     * copy intact. Idempotent: skips sessions that already have a GCS object,
     * lack a GridFS pointer, or when GCS isn't configured. Returns true when a
     * new GCS object was written.
     */
    public boolean backfillToGcs(CompletedSession session) {
        if (session == null || session.getFitFileId() == null) return false;
        if (session.getFitGcsObject() != null) return false;
        if (!isGcsAvailable()) return false;

        Optional<byte[]> bytes = readFromMongo(session);
        if (bytes.isEmpty()) return false;

        String objectName = objectName(session);
        writeToGcs(objectName, bytes.get());
        session.setFitGcsObject(objectName);
        return true;
    }

    public boolean isGcsAvailable() {
        return storageProvider.getIfAvailable() != null;
    }

    private Optional<byte[]> readFromMongo(CompletedSession session) {
        if (session.getFitFileId() == null) return Optional.empty();
        try {
            GridFSFile gridFile = gridFsOperations.findOne(
                    Query.query(Criteria.where("_id").is(new ObjectId(session.getFitFileId()))));
            if (gridFile == null) return Optional.empty();
            GridFsResource resource = gridFsOperations.getResource(gridFile);
            return Optional.of(resource.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.warn("Failed to read GridFS FIT {} for session {}: {}",
                    session.getFitFileId(), session.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<byte[]> readFromGcs(CompletedSession session) {
        if (session.getFitGcsObject() == null || !isGcsAvailable()) return Optional.empty();
        try {
            byte[] bytes = storageProvider.getObject()
                    .readAllBytes(BlobId.of(bucket(), session.getFitGcsObject()));
            return Optional.of(bytes);
        } catch (Exception e) {
            log.warn("Failed to read GCS FIT {} for session {}: {}",
                    session.getFitGcsObject(), session.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private void writeToGcs(String objectName, byte[] bytes) {
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket(), objectName))
                .setContentType(CONTENT_TYPE)
                .build();
        storageProvider.getObject().create(info, bytes);
    }

    private String objectName(CompletedSession session) {
        String userId = session.getUserId() == null ? "_orphan" : session.getUserId();
        return fitProperties.getObjectPrefix() + "/" + userId + "/" + session.getId() + ".fit";
    }

    private String bucket() {
        String configured = fitProperties.getBucket();
        if (configured != null && !configured.isBlank()) return configured;
        return mediaProperties.getBucket();
    }
}
