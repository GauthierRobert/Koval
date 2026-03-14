package com.koval.trainingplannerbackend.training.model;

public enum SportType {
    CYCLING(8.33),
    RUNNING(3.33),
    SWIMMING(1.00),
    BRICK(8.33);

    private final double typicalSpeedMps;

    SportType(double typicalSpeedMps) {
        this.typicalSpeedMps = typicalSpeedMps;
    }

    public double getTypicalSpeedMps() {
        return typicalSpeedMps;
    }
}
