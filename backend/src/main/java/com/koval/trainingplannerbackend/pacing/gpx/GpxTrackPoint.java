package com.koval.trainingplannerbackend.pacing.gpx;

public record GpxTrackPoint(
        double lat,
        double lon,
        double elevation,
        double cumulativeDistance
) {}
