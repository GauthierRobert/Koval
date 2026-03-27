package com.koval.trainingplannerbackend.training.zone;

/** The physiological reference metric used to define training zones (e.g. FTP, threshold pace, CSS). */
public enum ZoneReferenceType {
    FTP,
    VO2MAX_POWER,
    THRESHOLD_PACE,
    VO2MAX_PACE,
    CSS,
    PACE_5K,
    PACE_10K,
    PACE_HALF_MARATHON,
    PACE_MARATHON,
    CUSTOM
}
