package com.koval.trainingplannerbackend.pacing.gpx;

public record CourseSegment(
        double startDistance,
        double endDistance,
        double averageGradient,
        double elevationGain,
        double elevationLoss,
        double startElevation,
        double endElevation
) {
    public double length() {
        return endDistance - startDistance;
    }
}
