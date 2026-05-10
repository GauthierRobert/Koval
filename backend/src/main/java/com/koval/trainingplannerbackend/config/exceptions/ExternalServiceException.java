package com.koval.trainingplannerbackend.config.exceptions;

public class ExternalServiceException extends RuntimeException {

    private final String code;

    public ExternalServiceException(String service, String message) {
        super(service + ": " + message);
        this.code = "EXTERNAL_SERVICE_ERROR";
    }

    public ExternalServiceException(String service, String message, Throwable cause) {
        super(service + ": " + message, cause);
        this.code = "EXTERNAL_SERVICE_ERROR";
    }

    public String getCode() {
        return code;
    }
}
