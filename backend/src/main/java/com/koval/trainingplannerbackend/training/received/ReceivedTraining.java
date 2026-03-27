package com.koval.trainingplannerbackend.training.received;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Represents a training that was received by an athlete, either via direct coach
 * assignment, coach group broadcast, or club session linkage.
 */
@Getter
@Setter
@Document("received_trainings")
@CompoundIndex(name = "athlete_training_idx", def = "{'athleteId': 1, 'trainingId': 1}", unique = true)
public class ReceivedTraining {

    @Id
    private String id;

    @Indexed
    private String athleteId;

    private String trainingId;
    private String assignedBy;
    private String assignedByName;
    private ReceivedTrainingOrigin origin;
    private String originId;
    private String originName;
    private LocalDateTime receivedAt = LocalDateTime.now();
}
