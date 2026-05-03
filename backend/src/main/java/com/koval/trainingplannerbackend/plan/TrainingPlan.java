package com.koval.trainingplannerbackend.plan;

import com.koval.trainingplannerbackend.training.model.SportType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "training_plans")
@CompoundIndexes({
        @CompoundIndex(name = "athleteIds_status_idx", def = "{'athleteIds': 1, 'status': 1}")
})
public class TrainingPlan {

    @Id
    private String id;

    private String title;
    private String description;
    private SportType sportType = SportType.CYCLING;

    @Indexed
    private String createdBy;

    @Indexed
    private LocalDate startDate;
    private int durationWeeks;

    @Indexed
    private PlanStatus status = PlanStatus.DRAFT;

    private List<PlanWeek> weeks = new ArrayList<>();

    private String goalRaceId;    // optional link to RaceGoal
    private Integer targetFtp;    // target FTP at plan end

    @Indexed
    private List<String> athleteIds = new ArrayList<>(); // for coach-assigned plans

    private LocalDateTime createdAt;
    private LocalDateTime activatedAt;

    public TrainingPlan() {
        this.createdAt = LocalDateTime.now();
    }
}
