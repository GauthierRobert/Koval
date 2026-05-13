package com.koval.trainingplannerbackend.training.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class BrickTraining extends Training {
    public BrickTraining() {
        setSportType(SportType.BRICK);
    }
}
