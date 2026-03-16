package com.koval.trainingplannerbackend.config.exceptions;

public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String message) {
        super(message);
        this.code = "VALIDATION_ERROR";
    }

    public ValidationException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
