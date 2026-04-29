package com.koval.trainingplannerbackend.club.gazette;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "club_gazette_posts")
@CompoundIndexes({
        @CompoundIndex(name = "edition_author_idx", def = "{'editionId': 1, 'authorId': 1}"),
        @CompoundIndex(name = "edition_created_idx", def = "{'editionId': 1, 'createdAt': 1}")
})
public class ClubGazettePost {

    @Id
    private String id;

    @Indexed
    private String editionId;

    @Indexed
    private String clubId;

    private String authorId;
    private String authorDisplayName;
    private String authorProfilePicture;

    private GazettePostType type;

    private String title;          // optional, ≤ 100 chars
    private String content;        // required, ≤ 2000 chars

    private String linkedSessionId;
    private String linkedRaceGoalId;
    private LinkedSessionSnapshot linkedSessionSnapshot;
    private LinkedRaceGoalSnapshot linkedRaceGoalSnapshot;

    private List<String> mediaIds = new ArrayList<>();   // up to 4 photos

    /** Order in which this post appears in the published edition. Set by the publisher. */
    private Integer displayOrder;

    /** True for posts that were left out of the curation at publish time. */
    private boolean excluded;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Embedded records ─────────────────────────────────────────────────────

    public record LinkedSessionSnapshot(
            String sessionId,
            String title,
            String sport,
            LocalDateTime scheduledAt,
            String location) {}

    public record LinkedRaceGoalSnapshot(
            String raceGoalId,
            String title,
            String sport,
            LocalDate raceDate,
            String distance,
            String targetTime,
            String finishTime) {}
}
