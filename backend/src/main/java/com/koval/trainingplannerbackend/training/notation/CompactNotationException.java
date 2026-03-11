package com.koval.trainingplannerbackend.training.notation;

/** Thrown when compact workout notation cannot be parsed. */
public class CompactNotationException extends RuntimeException {
    public CompactNotationException(String message) {
        super(message);
    }
}
