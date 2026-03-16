package com.koval.trainingplannerbackend.config.exceptions;

public class RateLimitException extends RuntimeException {

    private final String code;

    public RateLimitException(String message) {
        super(message);
        this.code = "RATE_LIMIT_EXCEEDED";
    }

    public String getCode() {
        return code;
    }
}
