package com.koval.trainingplannerbackend.coach;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "scheduled_workouts")
public class ScheduledWorkout {
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
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    public ScheduledWorkout() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrainingId() {
        return trainingId;
    }

    public void setTrainingId(String trainingId) {
        this.trainingId = trainingId;
    }

    public String getAthleteId() {
        return athleteId;
    }

    public void setAthleteId(String athleteId) {
        this.athleteId = athleteId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getTss() {
        return tss;
    }

    public void setTss(Integer tss) {
        this.tss = tss;
    }

    public Double getIntensityFactor() {
        return intensityFactor;
    }

    public void setIntensityFactor(Double intensityFactor) {
        this.intensityFactor = intensityFactor;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
