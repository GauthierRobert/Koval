package com.koval.trainingplannerbackend.club.test;

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

/** A single round of a {@link ClubTest} (e.g. "2026"). The segment + rule definition is frozen onto the
 * iteration at creation so historic results stay interpretable even if the parent test is later edited. */
@Getter
@Setter
@Document(collection = "club_test_iterations")
@CompoundIndexes({
    @CompoundIndex(name = "testId_label", def = "{'testId': 1, 'label': 1}", unique = true)
})
public class ClubTestIteration {
    @Id
    private String id;

    @Indexed
    private String testId;

    @Indexed
    private String clubId;

    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private IterationStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    /** Frozen snapshot of {@link ClubTest#getSegments()} at iteration creation. */
    private List<TestSegment> segments = new ArrayList<>();
    /** Frozen snapshot of {@link ClubTest#getReferenceUpdates()} at iteration creation. */
    private List<ReferenceUpdateRule> referenceUpdates = new ArrayList<>();
}
