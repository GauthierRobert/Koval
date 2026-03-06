package com.koval.trainingplannerbackend.training.notation;

/** Thrown when workout notation cannot be parsed. */
@Deprecated(forRemoval = true)
public class WorkoutNotationException extends RuntimeException {
    public WorkoutNotationException(String message) {
        super(message);
    }
}
