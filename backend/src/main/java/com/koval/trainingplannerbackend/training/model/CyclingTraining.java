package com.koval.trainingplannerbackend.training.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class CyclingTraining extends Training {
    public CyclingTraining() {
        setSportType(SportType.CYCLING);
    }
}
