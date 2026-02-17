package com.koval.trainingplannerbackend.training.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RunningTraining extends Training {
    public RunningTraining() {
        setSportType(SportType.RUNNING);
    }
}
