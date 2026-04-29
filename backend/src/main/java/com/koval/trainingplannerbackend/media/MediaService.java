package com.koval.trainingplannerbackend.media;

import com.google.cloud.storage.Blob;
import com.koval.trainingplannerbackend.media.dto.ConfirmUploadResponse;
import com.koval.trainingplannerbackend.media.dto.MediaResponse;
import com.koval.trainingplannerbackend.media.dto.MediaVariantResponse;
import com.koval.trainingplannerbackend.media.dto.RequestUploadUrlRequest;
import com.koval.trainingplannerbackend.media.dto.RequestUploadUrlResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Higher-level operations on Media: issue upload URLs, validate/confirm uploads,
 * issue read URLs, delete. Authorisation against the user is performed here;
 * callers pass the acting userId.
 */
@Service
public class MediaService {

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic"
    );

    private final MediaRepository mediaRepository;
    private final MediaStorageService storage;
    private final MediaProcessingService processingService;

    public MediaService(MediaRepository mediaRepository,
                        MediaStorageService storage,
                        MediaProcessingService processingService) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
        this.processingService = processingService;
    }

    public RequestUploadUrlResponse requestUploadUrl(String userId, RequestUploadUrlRequest req) {
        if (req == null || req.purpose() == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        validateContentType(req.contentType());
        validateSize(req.sizeBytes());

        String mediaId = UUID.randomUUID().toString();
        String objectName = buildObjectName(req.purpose(), req.clubId(), userId, mediaId, req.contentType());

        String url = storage.signedUploadUrl(objectName, req.contentType(), req.sizeBytes()).toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(storage.getProperties().getSignedUrlUploadTtl());

        Media media = new Media();
        media.setId(mediaId);
        media.setOwnerId(userId);
        media.setClubId(req.clubId());
        media.setPurpose(req.purpose());
        media.setObjectName(objectName);
        media.setContentType(req.contentType());
        media.setSizeBytes(req.sizeBytes());
        media.setConfirmed(false);
        media.setCreatedAt(LocalDateTime.now());
        mediaRepository.save(media);

        return new RequestUploadUrlResponse(mediaId, objectName, url, req.contentType(), expiresAt);
    }

    public ConfirmUploadResponse confirmUpload(String userId, String mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        if (!media.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Only the uploader can confirm this media");
        }
        if (media.isConfirmed()) {
            return new ConfirmUploadResponse(media.getId(), true,
                    media.getSizeBytes(), media.getWidth(), media.getHeight(),
                    media.getProcessingStatus(), media.getBlurHash());
        }

        Blob blob = storage.getBlob(media.getObjectName())
                .orElseThrow(() -> new IllegalStateException(
                        "Object not found in GCS yet. Upload may not have completed."));

        long actualSize = blob.getSize() != null ? blob.getSize() : 0L;
        if (actualSize > storage.getProperties().getMaxUploadSizeBytes()) {
            storage.deleteObject(media.getObjectName());
            mediaRepository.delete(media);
            throw new IllegalArgumentException(
                    "Uploaded file exceeds maximum size " + storage.getProperties().getMaxUploadSizeBytes());
        }
        media.setSizeBytes(actualSize);
        media.setConfirmed(true);
        media.setConfirmedAt(LocalDateTime.now());
        mediaRepository.save(media);

        // Run the optimisation pipeline synchronously: variants, BlurHash, dimensions.
        // The pipeline saves the Media doc with READY/FAILED status.
        byte[] originalBytes = blob.getContent();
        processingService.process(media, originalBytes);

        return new ConfirmUploadResponse(
                media.getId(), true, media.getSizeBytes(),
                media.getWidth(), media.getHeight(),
                media.getProcessingStatus(), media.getBlurHash());
    }

    public MediaResponse getReadResponse(String mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        if (!media.isConfirmed()) {
            throw new IllegalStateException("Media not confirmed yet");
        }
        return buildMediaResponse(media);
    }

    public MediaResponse buildMediaResponse(Media media) {
        String originalUrl = storage.signedReadUrl(media.getObjectName()).toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(storage.getProperties().getSignedUrlReadTtl());

        Map<String, MediaVariantResponse> variantUrls = new LinkedHashMap<>();
        if (media.getVariants() != null) {
            for (MediaVariant variant : media.getVariants()) {
                String url = storage.signedReadUrl(variant.objectName()).toString();
                variantUrls.put(variant.label(), new MediaVariantResponse(
                        url, variant.contentType(), variant.width(), variant.height(), variant.sizeBytes()));
            }
        }
        return new MediaResponse(
                media.getId(),
                media.getPurpose(),
                media.getContentType(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getBlurHash(),
                media.getProcessingStatus(),
                originalUrl,
                variantUrls,
                expiresAt);
    }

    public void delete(String userId, String mediaId, boolean adminOverride) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found"));
        if (!adminOverride && !media.getOwnerId().equals(userId)) {
            throw new IllegalStateException("Only the owner can delete this media");
        }
        storage.deleteObject(media.getObjectName());
        mediaRepository.delete(media);
    }

    public Optional<Media> findById(String mediaId) {
        return mediaRepository.findById(mediaId);
    }

    /**
     * Verify that the given {@code mediaIds} were uploaded by {@code userId}, are
     * confirmed, and have the expected purpose. Used by feature services (gazette
     * posts, feed enrichments) before persisting references.
     */
    public void requireOwnedAndConfirmed(String userId, Iterable<String> mediaIds, MediaPurpose expectedPurpose) {
        for (String mediaId : mediaIds) {
            Media m = mediaRepository.findById(mediaId)
                    .orElseThrow(() -> new IllegalArgumentException("Media not found: " + mediaId));
            if (!m.getOwnerId().equals(userId)) {
                throw new IllegalStateException("Media " + mediaId + " not owned by user");
            }
            if (!m.isConfirmed()) {
                throw new IllegalStateException("Media " + mediaId + " not confirmed");
            }
            if (m.getPurpose() != expectedPurpose) {
                throw new IllegalStateException(
                        "Media " + mediaId + " purpose mismatch: expected " + expectedPurpose);
            }
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType is required");
        }
        if (!storage.getProperties().getAllowedContentTypes().contains(contentType)) {
            throw new IllegalArgumentException("Unsupported contentType: " + contentType);
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        if (sizeBytes > storage.getProperties().getMaxUploadSizeBytes()) {
            throw new IllegalArgumentException("File exceeds maximum size of "
                    + storage.getProperties().getMaxUploadSizeBytes() + " bytes");
        }
    }

    private String buildObjectName(MediaPurpose purpose, String clubId, String userId,
                                   String mediaId, String contentType) {
        String scope = clubId != null && !clubId.isBlank() ? clubId : userId;
        String yearMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String ext = MIME_TO_EXTENSION.getOrDefault(contentType, "bin");
        return purposeFolder(purpose) + "/" + scope + "/" + yearMonth + "/" + mediaId + "." + ext;
    }

    private String purposeFolder(MediaPurpose purpose) {
        return switch (purpose) {
            case GAZETTE_POST -> "gazette-post";
            case FEED_POST_ENRICHMENT -> "feed-post";
            case AVATAR -> "avatar";
        };
    }

}
