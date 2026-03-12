package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "simulation_requests")
public class SimulationRequest {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String raceId;
    private String goalId;

    private String discipline; // SWIM | BIKE | RUN | TRIATHLON
    private AthleteProfile athleteProfile;
    private int bikeLoops = 1;
    private int runLoops = 1;
    private String label;

    private LocalDateTime createdAt;
}
