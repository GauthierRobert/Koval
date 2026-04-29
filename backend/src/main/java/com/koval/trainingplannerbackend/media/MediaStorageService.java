package com.koval.trainingplannerbackend.media;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Thin façade over Google Cloud Storage. Issues V4 signed URLs for uploads and
 * downloads, and exposes a few existence/delete helpers.
 *
 * The {@link Storage} bean is only created when {@code storage.gcs.enabled=true},
 * so this service is auto-disabled in environments where GCS is not configured —
 * callers receive an {@link IllegalStateException} on use.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private final ObjectProvider<Storage> storageProvider;
    private final MediaStorageProperties properties;

    public MediaStorageService(ObjectProvider<Storage> storageProvider,
                               MediaStorageProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && storageProvider.getIfAvailable() != null;
    }

    public MediaStorageProperties getProperties() {
        return properties;
    }

    /**
     * Issue a V4 signed URL the client can PUT the file to. Conditions on
     * Content-Type and Content-Length-Range are embedded so the bucket cannot
     * accept oversized uploads or wrong MIME types.
     */
    public URL signedUploadUrl(String objectName, String contentType, long sizeBytes) {
        Storage storage = requireStorage();

        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName))
                .setContentType(contentType)
                .build();

        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", contentType);

        try {
            return storage.signUrl(
                    blobInfo,
                    properties.getSignedUrlUploadTtl().toMinutes(),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withExtHeaders(extensionHeaders),
                    Storage.SignUrlOption.withV4Signature());
        } catch (IllegalStateException e) {
            throw signingMisconfigured(e);
        }
    }

    /**
     * Issue a V4 signed GET URL valid for the configured read TTL.
     */
    public URL signedReadUrl(String objectName) {
        Storage storage = requireStorage();
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName)).build();
        try {
            return storage.signUrl(
                    blobInfo,
                    properties.getSignedUrlReadTtl().toMinutes(),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature());
        } catch (IllegalStateException e) {
            throw signingMisconfigured(e);
        }
    }

    private IllegalStateException signingMisconfigured(IllegalStateException cause) {
        String msg = cause.getMessage() == null ? "" : cause.getMessage();
        if (msg.contains("Signing key") || msg.contains("signing key")) {
            return new IllegalStateException(
                    "Cannot sign GCS URL: the configured credentials have no private key. "
                            + "Set storage.gcs.credentials-path to a service-account JSON file, "
                            + "or storage.gcs.signer-service-account to an SA email for IAM-impersonated signing.",
                    cause);
        }
        return cause;
    }

    /**
     * Look up an object's metadata. Returns empty when the object is missing.
     */
    public Optional<Blob> getBlob(String objectName) {
        Storage storage = requireStorage();
        Blob blob = storage.get(BlobId.of(properties.getBucket(), objectName));
        return Optional.ofNullable(blob);
    }

    /**
     * Upload bytes to GCS at the given object name with the given Content-Type and
     * Cache-Control header. Used by the processing pipeline to write resized variants.
     */
    public void uploadObject(String objectName, String contentType, byte[] data, String cacheControl) {
        Storage storage = requireStorage();
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(properties.getBucket(), objectName))
                .setContentType(contentType)
                .setCacheControl(cacheControl)
                .build();
        storage.create(info, data);
    }

    /**
     * Delete an object. Returns true if the object existed and was removed.
     * Logs and swallows any error so cleanup paths don't fail loudly.
     */
    public boolean deleteObject(String objectName) {
        Storage storage = requireStorage();
        try {
            return storage.delete(BlobId.of(properties.getBucket(), objectName));
        } catch (Exception e) {
            log.warn("Failed to delete GCS object {}: {}", objectName, e.getMessage());
            return false;
        }
    }

    private Storage requireStorage() {
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalStateException(
                    "GCS is not configured (storage.gcs.enabled=false). Cannot perform media storage operation.");
        }
        return storage;
    }
}
