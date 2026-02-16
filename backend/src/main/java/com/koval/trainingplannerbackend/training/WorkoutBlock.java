package com.koval.trainingplannerbackend.training;

public record WorkoutBlock(
        BlockType type,
        int durationSeconds,
        Integer distanceMeters,
        String label,
        String zoneLabel,
        // Cycling specific
        Integer powerTargetPercent,
        Integer powerStartPercent,
        Integer powerEndPercent,
        Integer cadenceTarget,
        // Running specific
        Integer paceTargetSecondsPerKm,
        Integer paceStartSecondsPerKm,
        Integer paceEndSecondsPerKm,
        // Swimming specific
        Integer swimPacePer100m,
        Integer swimStrokeRate) {

    // Canonical constructor or builder could be added if needed,
    // but for now, we'll use the record constructor.
    // Jackson handles records well in modern versions.
}
