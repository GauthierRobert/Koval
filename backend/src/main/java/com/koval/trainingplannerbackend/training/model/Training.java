package com.koval.trainingplannerbackend.training.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "trainings")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "sportType", visible = true, defaultImpl = CyclingTraining.class)
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
    @NotBlank
    private String title;
    private String description;
    private List<WorkoutElement> blocks = new ArrayList<>();
    private LocalDateTime createdAt;
    private Integer estimatedTss;
    private Double estimatedIf;
    private Integer estimatedDurationSeconds;
    private Integer estimatedDistance;
    private String zoneSystemId;
    private TrainingType trainingType;
    private SportType sportType = SportType.CYCLING;
    // New fields for coaching support
    @Indexed
    private String createdBy; // User ID of creator
    private List<String> groupIds = new ArrayList<>(); // ID list of coach Group IDs

    // Club linking — a training can belong to multiple clubs
    @Indexed
    private List<String> clubIds = new ArrayList<>();
    private List<String> clubGroupIds = new ArrayList<>(); // Optional: club groups within clubs

    /** Adds the given club ID to this training's club list (idempotent, null-safe). */
    public void addClubId(String clubId) {
        if (clubId != null && !this.clubIds.contains(clubId)) {
            this.clubIds.add(clubId);
        }
    }

}
