package com.koval.trainingplannerbackend.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "media")
@CompoundIndexes({
        @CompoundIndex(name = "owner_purpose_idx", def = "{'ownerId': 1, 'purpose': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "confirmed_created_idx", def = "{'confirmed': 1, 'createdAt': 1}")
})
public class Media {

    @Id
    private String id;

    @Indexed
    private String ownerId;

    private String clubId;

    private MediaPurpose purpose;

    private String objectName;

    private String contentType;

    private long sizeBytes;

    private Integer width;
    private Integer height;

    private boolean confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    private String blurHash;
    private List<MediaVariant> variants = new ArrayList<>();
    private MediaProcessingStatus processingStatus;
    private LocalDateTime processedAt;
    private String processingError;
}
