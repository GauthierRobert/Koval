package com.koval.trainingplannerbackend.club.test;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "club_tests")
public class ClubTest {
    @Id
    private String id;

    @Indexed
    private String clubId;

    private String name;
    private String description;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private boolean competitionMode;
    private RankingMetric rankingMetric;
    /** Either a segmentId (for {@code TIME_OF_SEGMENT}) or a ruleId (for {@code COMPUTED_REFERENCE}); ignored for {@code SUM_OF_TIMES}. */
    private String rankingTarget;
    private RankingDirection rankingDirection;

    private List<TestSegment> segments = new ArrayList<>();
    private List<ReferenceUpdateRule> referenceUpdates = new ArrayList<>();

    /** Denormalized pointer to the currently OPEN iteration; null when none. */
    private String currentIterationId;
    private boolean archived;
}
