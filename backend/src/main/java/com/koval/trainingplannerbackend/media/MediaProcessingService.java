package com.koval.trainingplannerbackend.media;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Synchronous image-processing pipeline run during upload confirmation:
 * computes a BlurHash placeholder and generates four resolution variants
 * (thumb / small / medium / large) of every confirmed image, uploading each
 * variant to GCS with an immutable cache header.
 *
 * Variants are JPEG quality 80 in v1 (no native deps). WebP can be substituted
 * later by adding the appropriate ImageIO plugin without changing the API.
 *
 * For HEIC originals, the JDK has no built-in decoder — processing fails
 * gracefully with status FAILED, but the original remains usable via its
 * signed URL.
 */
@Service
public class MediaProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);

    private static final String IMMUTABLE_CACHE = "public, max-age=31536000, immutable";
    private static final String VARIANT_MIME = "image/jpeg";
    private static final float JPEG_QUALITY = 0.80f;
    private static final int BLURHASH_DOWNSAMPLE = 64;
    private static final int BLURHASH_X_COMPONENTS = 4;
    private static final int BLURHASH_Y_COMPONENTS = 3;

    private static final List<VariantSpec> VARIANTS = List.of(
            new VariantSpec("thumb", 240),
            new VariantSpec("small", 480),
            new VariantSpec("medium", 960),
            new VariantSpec("large", 1920)
    );

    private final MediaRepository mediaRepository;
    private final MediaStorageService storage;

    public MediaProcessingService(MediaRepository mediaRepository, MediaStorageService storage) {
        this.mediaRepository = mediaRepository;
        this.storage = storage;
    }

    /**
     * Run the pipeline using already-fetched original bytes (so confirmUpload
     * doesn't have to download from GCS twice). Mutates and saves the Media doc.
     */
    public void process(Media media, byte[] originalBytes) {
        // Skip image variant generation for non-image attachments (PDF, docs, etc.).
        // The original is still served verbatim through its signed URL.
        String contentType = media.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            media.setProcessingStatus(MediaProcessingStatus.READY);
            media.setProcessingError(null);
            media.setProcessedAt(LocalDateTime.now());
            mediaRepository.save(media);
            return;
        }

        media.setProcessingStatus(MediaProcessingStatus.PENDING);
        try {
            BufferedImage source;
            try {
                source = ImageIO.read(new ByteArrayInputStream(originalBytes));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot decode image: " + e.getMessage(), e);
            }
            if (source == null) {
                throw new IllegalStateException(
                        "Unsupported image format: " + media.getContentType());
            }

            media.setWidth(source.getWidth());
            media.setHeight(source.getHeight());

            media.setBlurHash(computeBlurHash(source));
            media.setVariants(generateVariants(source, media.getObjectName()));

            media.setProcessingStatus(MediaProcessingStatus.READY);
            media.setProcessingError(null);
            media.setProcessedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Image processing failed for media {}: {}", media.getId(), e.getMessage());
            media.setProcessingStatus(MediaProcessingStatus.FAILED);
            media.setProcessingError(truncate(e.getMessage(), 500));
            media.setProcessedAt(LocalDateTime.now());
        }
        mediaRepository.save(media);
    }

    private String computeBlurHash(BufferedImage source) throws IOException {
        BufferedImage downsample = Thumbnails.of(source)
                .size(BLURHASH_DOWNSAMPLE, BLURHASH_DOWNSAMPLE)
                .asBufferedImage();
        return BlurHashEncoder.encode(downsample, BLURHASH_X_COMPONENTS, BLURHASH_Y_COMPONENTS);
    }

    private List<MediaVariant> generateVariants(BufferedImage source, String originalObjectName)
            throws IOException {
        String basePath = stripExtension(originalObjectName);
        List<MediaVariant> variants = new ArrayList<>();
        for (VariantSpec spec : VARIANTS) {
            if (spec.targetWidth() >= source.getWidth()) {
                // Don't upscale: smaller variants would be larger than the original.
                continue;
            }
            double scale = (double) spec.targetWidth() / source.getWidth();
            int newWidth = spec.targetWidth();
            int newHeight = (int) Math.round(source.getHeight() * scale);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(source)
                    .size(newWidth, newHeight)
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toOutputStream(out);
            byte[] bytes = out.toByteArray();

            String variantObjectName = basePath + "/" + spec.label() + ".jpg";
            storage.uploadObject(variantObjectName, VARIANT_MIME, bytes, IMMUTABLE_CACHE);

            variants.add(new MediaVariant(
                    spec.label(), variantObjectName, VARIANT_MIME,
                    newWidth, newHeight, bytes.length));
        }
        return variants;
    }

    private static String stripExtension(String objectName) {
        int slash = objectName.lastIndexOf('/');
        int dot = objectName.lastIndexOf('.');
        if (dot > slash) {
            return objectName.substring(0, dot);
        }
        return objectName;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record VariantSpec(String label, int targetWidth) {}
}
