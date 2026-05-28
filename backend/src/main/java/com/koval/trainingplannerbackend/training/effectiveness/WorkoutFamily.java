package com.koval.trainingplannerbackend.training.effectiveness;

/**
 * Intensity-based grouping of completed sessions used by the effectiveness algorithm.
 * Classification is heuristic — see {@link WorkoutFamilyClassifier}.
 */
public enum WorkoutFamily {
    RECOVERY,
    ENDURANCE,
    TEMPO,
    SWEET_SPOT,
    THRESHOLD,
    VO2MAX,
    SPRINT,
    MIXED
}
