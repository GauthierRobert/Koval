package com.koval.trainingplannerbackend.training;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwimmingTraining extends Training {
    public SwimmingTraining() {
        setSportType(SportType.SWIMMING);
    }
}
