package com.koval.trainingplannerbackend.goal;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

    private String title;
    private String sport;        // CYCLING | RUNNING | SWIMMING | TRIATHLON | OTHER
    private LocalDate raceDate;
    private String priority;     // A | B | C
    private String distance;
    private String location;
    private String notes;
    private String targetTime;
    private LocalDateTime createdAt;
}
