package com.koval.trainingplannerbackend.training.history;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Document(collection = "completed_sessions")
public class CompletedSession {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String trainingId;
    private String title;
    private LocalDateTime completedAt;
    private int totalDurationSeconds;
    private double avgPower;
    private double avgHR;
    private double avgCadence;
    private String sportType;
    private List<BlockSummary> blockSummaries;

    public record BlockSummary(
            String label,
            String type,
            int durationSeconds,
            double targetPower,
            double actualPower,
            double actualCadence,
            double actualHR
    ) {}
}
