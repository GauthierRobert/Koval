package com.koval.trainingplannerbackend.training;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@Document(collection = "trainings")
public class Training {
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

    private TrainingType trainingType;

    // New fields for coaching support
    private String createdBy; // User ID of creator
    private TrainingVisibility visibility = TrainingVisibility.PRIVATE;
    private List<String> tags = new ArrayList<>();

    public Training() {
    }

}
