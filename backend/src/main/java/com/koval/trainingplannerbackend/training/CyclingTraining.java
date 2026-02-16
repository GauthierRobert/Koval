package com.koval.trainingplannerbackend.training;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CyclingTraining extends Training {
    public CyclingTraining() {
        setSportType(SportType.CYCLING);
    }
}
