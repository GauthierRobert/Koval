package com.koval.trainingplannerbackend.coach;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@Document(collection = "scheduled_workouts")
@CompoundIndex(name = "athlete_date_idx", def = "{'athleteId': 1, 'scheduledDate': 1}")
public class ScheduledWorkout {
    // Getters and Setters
    @Id
    private String id;

    private String trainingId; // Reference to Training document
    @Indexed
    private String planId;     // Reference to TrainingPlan document (if part of a plan)
    @Indexed
    private String athleteId; // User ID of the athlete
    @Indexed
    private String assignedBy; // User ID of the coach who assigned
    private LocalDate scheduledDate;
    private ScheduleStatus status = ScheduleStatus.PENDING;
    private String notes; // Coach notes for this assignment
    private Integer tss;
    private Double intensityFactor;
    private String sessionId; // Reference to CompletedSession
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public ScheduledWorkout() {
        this.createdAt = LocalDateTime.now();
    }

}
