package com.koval.trainingplannerbackend.club.test;

/** Reference value on the {@code User} document that a test rule can write into. {@code CUSTOM} writes
 * to {@code customZoneReferenceValues} keyed by the rule's {@code customKey}. */
public enum ReferenceTarget {
    FTP,
    CRITICAL_SWIM_SPEED,
    FUNCTIONAL_THRESHOLD_PACE,
    PACE_5K,
    PACE_10K,
    PACE_HALF_MARATHON,
    PACE_MARATHON,
    VO2MAX_POWER,
    VO2MAX_PACE,
    POWER_3MIN,
    POWER_12MIN,
    WEIGHT_KG,
    CUSTOM
}
