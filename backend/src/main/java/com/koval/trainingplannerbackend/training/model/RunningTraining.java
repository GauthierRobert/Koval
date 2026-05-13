package com.koval.trainingplannerbackend.training.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class RunningTraining extends Training {
    public RunningTraining() {
        setSportType(SportType.RUNNING);
    }
}
