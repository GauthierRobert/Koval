package com.koval.trainingplannerbackend.training.model;

/** The kind of segment within a structured workout (warmup, steady-state, interval, transition, etc.). */
public enum BlockType {
    WARMUP,
    STEADY,
    INTERVAL,
    COOLDOWN,
    RAMP,
    FREE,
    PAUSE,
    TRANSITION
}
