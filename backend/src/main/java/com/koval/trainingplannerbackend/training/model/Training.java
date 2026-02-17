package com.koval.trainingplannerbackend.training.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trainings")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "sportType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CyclingTraining.class, name = "CYCLING"),
        @JsonSubTypes.Type(value = RunningTraining.class, name = "RUNNING"),
        @JsonSubTypes.Type(value = SwimmingTraining.class, name = "SWIMMING"),
        @JsonSubTypes.Type(value = BrickTraining.class, name = "BRICK")
})
public abstract class Training {
    // Getters/Setters
    @Id
    private String id;
    private String title;
    private String description;
    private List<WorkoutBlock> blocks;
    private LocalDateTime createdAt;
    private String chatHistoryId;
    private Integer estimatedTss;
    private Double estimatedIf;
    private Integer estimatedDurationSeconds;
    private Integer estimatedDistance;

    public Integer getTotalDurationSeconds() {
        return estimatedDurationSeconds;
    }

    private TrainingType trainingType;
    private SportType sportType = SportType.CYCLING;

    // New fields for coaching support
    private String createdBy; // User ID of creator
    private List<String> tags = new ArrayList<>();

}
