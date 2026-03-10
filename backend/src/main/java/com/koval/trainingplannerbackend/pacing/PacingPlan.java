package com.koval.trainingplannerbackend.pacing;

import com.koval.trainingplannerbackend.pacing.dto.AthleteProfile;
import com.koval.trainingplannerbackend.pacing.dto.PacingSegment;
import com.koval.trainingplannerbackend.pacing.dto.PacingSummary;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Document(collection = "pacing_plans")
public class PacingPlan {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private LocalDateTime createdAt = LocalDateTime.now();

    private AthleteProfile athleteProfile;
    private List<PacingSegment> bikeSegments;
    private List<PacingSegment> runSegments;
    private PacingSummary bikeSummary;
    private PacingSummary runSummary;
}
