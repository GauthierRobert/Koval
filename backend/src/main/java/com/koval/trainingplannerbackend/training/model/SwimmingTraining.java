package com.koval.trainingplannerbackend.training.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwimmingTraining extends Training {

    /** Pool length in meters (25 or 50). Null for open-water sessions. */
    private Integer poolLengthMeters;

    public SwimmingTraining() {
        setSportType(SportType.SWIMMING);
    }
}
