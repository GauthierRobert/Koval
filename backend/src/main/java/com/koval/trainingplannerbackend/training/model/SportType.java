package com.koval.trainingplannerbackend.training.model;

/** Supported sport disciplines, each carrying a typical speed (m/s) used for distance/duration estimation. */
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

    /** Parses a sport name (case-insensitive), defaulting to {@link #CYCLING} for null or unknown values. */
    public static SportType fromString(String sport) {
        if (sport == null) return CYCLING;
        try {
            return valueOf(sport.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CYCLING;
        }
    }

    /** Parses a sport name (case-insensitive), returning {@code null} for null or unknown values. */
    public static SportType fromStringOrNull(String sport) {
        if (sport == null) return null;
        try {
            return valueOf(sport.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
