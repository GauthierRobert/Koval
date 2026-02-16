package com.koval.trainingplannerbackend.training;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrickTraining extends Training {
    public BrickTraining() {
        setSportType(SportType.BRICK);
    }
}
