package com.koval.trainingplannerbackend.training.received;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAthleteId() { return athleteId; }
    public void setAthleteId(String athleteId) { this.athleteId = athleteId; }

    public String getTrainingId() { return trainingId; }
    public void setTrainingId(String trainingId) { this.trainingId = trainingId; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public String getAssignedByName() { return assignedByName; }
    public void setAssignedByName(String assignedByName) { this.assignedByName = assignedByName; }

    public ReceivedTrainingOrigin getOrigin() { return origin; }
    public void setOrigin(ReceivedTrainingOrigin origin) { this.origin = origin; }

    public String getOriginId() { return originId; }
    public void setOriginId(String originId) { this.originId = originId; }

    public String getOriginName() { return originName; }
    public void setOriginName(String originName) { this.originName = originName; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
