package com.koval.trainingplannerbackend.config.exceptions;

public class ForbiddenOperationException extends RuntimeException {

    private final String code;

    public ForbiddenOperationException(String message) {
        super(message);
        this.code = "FORBIDDEN";
    }

    public ForbiddenOperationException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
