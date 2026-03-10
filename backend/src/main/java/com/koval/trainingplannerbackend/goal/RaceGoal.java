package com.koval.trainingplannerbackend.goal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "race_goals")
public class RaceGoal {

    @Id
    private String id;

    @Indexed
    private String athleteId;

    @NotBlank
    private String title;
    private String sport;        // CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER
    @NotNull
    private LocalDate raceDate;
    private String priority;     // A | B | C
    private String distance;
    private String location;
    private String notes;
    private String targetTime;
    private LocalDateTime createdAt;
}
