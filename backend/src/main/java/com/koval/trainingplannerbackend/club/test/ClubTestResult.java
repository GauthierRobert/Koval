package com.koval.trainingplannerbackend.club.test;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One athlete's result for a {@link ClubTestIteration}. Unique per (iteration, athlete). */
@Getter
@Setter
@Document(collection = "club_test_results")
@CompoundIndexes({
    @CompoundIndex(name = "iterationId_athleteId", def = "{'iterationId': 1, 'athleteId': 1}", unique = true)
})
public class ClubTestResult {
    @Id
    private String id;

    @Indexed
    private String iterationId;

    @Indexed
    private String testId;

    @Indexed
    private String clubId;

    @Indexed
    private String athleteId;

    /** segmentId → recorded value (number + unit + optional CompletedSession link). */
    private Map<String, SegmentResultValue> segmentResults = new HashMap<>();
    /** ruleId → value produced by the rule's formula (recomputed on result save). */
    private Map<String, Double> computedReferences = new HashMap<>();
    /** Audit log of reference value writes to the athlete's User document. Append-only. */
    private List<AppliedReferenceUpdate> appliedUpdates = new ArrayList<>();

    /** Set only when the iteration's parent test has {@code competitionMode == true}. */
    private Integer rank;

    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String recordedBy;
}
