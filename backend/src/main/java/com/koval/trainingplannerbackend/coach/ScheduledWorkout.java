package com.koval.trainingplannerbackend.coach;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Setter
@Getter
@Document(collection = "scheduled_workouts")
public class ScheduledWorkout {
    // Getters and Setters
    @Id
    private String id;

    private String trainingId; // Reference to Training document
    private String athleteId; // User ID of the athlete
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
